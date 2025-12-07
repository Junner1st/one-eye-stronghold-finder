package net.hopper4et.oneeyestrongholdfinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.awt.*;

public class OneEyeStrongholdFinderClient implements ClientModInitializer {


	@Override
	public void onInitializeClient() {
		ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity.getType() != EntityType.EYE_OF_ENDER) return;
			if (MainThread.DEBUG) {
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
			new Thread(new MainThread(entity)).start();
		});
	}
}