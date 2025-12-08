package net.hopper4et.oneeyestrongholdfinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class OneEyeStrongholdFinderClient implements ClientModInitializer {

    private static final Map<Integer, CompletableFuture<Vec3d>> eyeRemovalPositions = new ConcurrentHashMap<>();
	private static final boolean DEBUG = true;
	private static volatile boolean debugMode = DEBUG;

	public static void setDebugMode(boolean enabled) {
		debugMode = enabled;
	}

	public static boolean isDebugMode() {
		return debugMode;
	}


	@Override
	public void onInitializeClient() {
		ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity.getType() != EntityType.EYE_OF_ENDER) return;
			CompletableFuture<Vec3d> removalFuture = new CompletableFuture<>();
			CompletableFuture<Vec3d> previous = eyeRemovalPositions.put(entity.getId(), removalFuture);
			if (previous != null && !previous.isDone()) {
				previous.complete(new Vec3d(entity.getX(), entity.getY(), entity.getZ()));
			}
			if (isDebugMode()) {
				MinecraftClient client = MinecraftClient.getInstance();
				if (client != null) {
					client.execute(() -> {
						ClientPlayerEntity player = client.player;
						if (player == null) return;
						player.sendMessage(Text.literal(String.format(
							"[OESF DEBUG] Eye loaded id=%d pos=%s",
							entity.getId(),
							String.format("(%.2f, %.2f, %.2f)", entity.getX(), entity.getY(), entity.getZ())
						)).setStyle(Style.EMPTY.withColor(Color.ORANGE.getRGB())), false);
					});
				}
			}
			new Thread(new MainThread(entity, removalFuture, isDebugMode())).start();
		});

		ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (entity.getType() != EntityType.EYE_OF_ENDER) return;
			CompletableFuture<Vec3d> future = eyeRemovalPositions.remove(entity.getId());
			if (future != null && !future.isDone()) {
				future.complete(new Vec3d(entity.getX(), entity.getY(), entity.getZ()));
			}
		});
	}
}