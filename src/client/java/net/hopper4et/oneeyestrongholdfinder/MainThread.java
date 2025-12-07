package net.hopper4et.oneeyestrongholdfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MainThread extends Thread {
    public static final boolean DEBUG = true;
    private static final int MAX_STEPS_TO_RING = 1600;
    private final Entity eyeOfEnder;
    private final CompletableFuture<Vec3d> removalPositionFuture;
    public MainThread(Entity eyeOfEnder, CompletableFuture<Vec3d> removalPositionFuture) {
        this.eyeOfEnder = eyeOfEnder;
        this.removalPositionFuture = removalPositionFuture;
    }

    @Override
    public void run() {
        debugMessage("Thread started for Eye #" + eyeOfEnder.getId());
        if (eyeOfEnder.isRemoved()) {
            debugMessage("Eye entity was removed before first capture");
            return;
        }
        //взятие первых и вторых координат
        Vec3d pos1 = captureEyePosition();
        debugMessage("Captured first position " + formatVec(pos1));
        Vec3d pos2 = waitForRemovalPosition(pos1);
        debugMessage("Captured final position before removal " + formatVec(pos2));
        if (pos1.x == pos2.x || pos1.z == pos2.z) {
            debugMessage("Eye positions did not diverge enough; aborting");
            return;
        }

        //менять координаты для точности
        boolean isReverse = false;
        if (Math.abs(pos1.z - pos2.z) - Math.abs(pos1.x - pos2.x) > 0) {
            pos1 = new Vec3d(pos1.z, pos1.y, pos1.x);
            pos2 = new Vec3d(pos2.z, pos2.y, pos2.x);
            isReverse = true;
            debugMessage("Swapped axes for improved precision");
        }

        //создаёт искателя координат
        double slope = (pos2.z - pos1.z) / (pos2.x - pos1.x);
        int signX = (pos2.x - pos1.x) < 0 ? -1 : 1;
        debugMessage(String.format("Slope=%.5f signX=%d", slope, signX));
        GridFinder finder = new GridFinder(
                pos1.x, pos1.z,
                slope,
                signX
        );

        //идёт до кольца
        int stepsToRing = 0;
        while (!finder.isInRing()) {
            finder.next();
            stepsToRing++;
            if (stepsToRing > MAX_STEPS_TO_RING) {
                double distance = Math.hypot(finder.x, finder.z);
                debugMessage(String.format(
                        "Aborted: exceeded %d steps before entering ring (distance %.2f)",
                        MAX_STEPS_TO_RING,
                        distance
                ));
                return;
            }
        }
        debugMessage("Reached stronghold ring after " + stepsToRing + " iterations");

        //идёт по кольцу
        ArrayList<Stronghold> strongholds = new ArrayList<>();
        int ringSamples = 0;
        while (finder.isInRing()) {
            Stronghold stronghold = finder.next();
            strongholds.add(stronghold);
            ringSamples++;
        }
        debugMessage("Collected " + ringSamples + " samples while traversing ring");
        if (strongholds.isEmpty()) {
            debugMessage("No strongholds collected");
            return;
        }

        //сортирует
        strongholds.sort(Comparator.comparingDouble((Stronghold a) -> a.accuracy).reversed());
        debugMessage(String.format(
                "Top accuracy %.2f, 5th accuracy %.2f",
                strongholds.get(0).accuracy,
                strongholds.get(Math.min(4, strongholds.size() - 1)).accuracy
        ));

        //создание переменных для цвета чата
        double topAccuracy = strongholds.get(0).accuracy;
        double minAccuracy = strongholds.get(Math.min(4, strongholds.size() - 1)).accuracy;
        double accuracySpread = topAccuracy - minAccuracy;
        if (!Double.isFinite(accuracySpread) || accuracySpread <= 0) {
            accuracySpread = 1;
        }

        //вывод информации в чат
        sendChat(Text.literal("=== One Eye Stronghold Finder ===").setStyle(
            Style.EMPTY.withColor(new Color(155, 251, 255).getRGB())
        ));

        for (int i = 0; i < Math.min(strongholds.size(), 5); i++) {
            double normalized = strongholds.size() > 1
                ? (strongholds.get(i).accuracy - minAccuracy) / accuracySpread
                : 1;
            if (!Double.isFinite(normalized)) normalized = 1;
            normalized = Math.max(0, Math.min(1, normalized));
            int color = (int) Math.round(normalized * 510);

            int overworldX = (isReverse ? strongholds.get(i).z : strongholds.get(i).x) + 3;
            int overworldZ = (isReverse ? strongholds.get(i).x : strongholds.get(i).z) + 3;
            int netherX = overworldX / 8;
            int netherZ = overworldZ / 8;

            String clickText = overworldX + " ~ " + overworldZ;
            String accuracyText = Double.isFinite(strongholds.get(i).accuracy)
                ? String.format("%.2f", strongholds.get(i).accuracy)
                : "INF";

            sendChat(Text.literal(String.format(
                "X: %- 6d Z: %- 6d (Nether: X: %- 6d Z: %- 6d) accuracy: %s",
                    overworldX, overworldZ,
                    netherX, netherZ,
                accuracyText
            )).setStyle(Style.EMPTY
                .withColor(new Color(
                    Math.max(0, Math.min(255, 510 - color)),
                    Math.max(0, Math.min(255, color)),
                    2
                ).getRGB())
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Copy to clipboard")))
                    .withClickEvent(new ClickEvent.CopyToClipboard(clickText))
            ));
        }
        debugMessage("Finished sending results");
    }

    private Vec3d captureEyePosition() {
        return new Vec3d(eyeOfEnder.getX(), eyeOfEnder.getY(), eyeOfEnder.getZ());
    }

    private void debugMessage(String message) {
        if (!DEBUG) return;
        sendChat(Text.literal("[OESF DEBUG] " + message).setStyle(
                Style.EMPTY.withColor(Color.GRAY.getRGB())
        ));
    }

    private String formatVec(Vec3d vec3d) {
        return String.format("(%.2f, %.2f, %.2f)", vec3d.x, vec3d.y, vec3d.z);
    }

    private Vec3d waitForRemovalPosition(Vec3d fallback) {
        if (removalPositionFuture == null) {
            debugMessage("Missing removal future; using fallback position");
            return fallback;
        }
        try {
            Vec3d result = removalPositionFuture.get(10, TimeUnit.SECONDS);
            if (result == null) {
                debugMessage("Removal future completed with null; using fallback");
                return fallback;
            }
            return result;
        } catch (TimeoutException e) {
            debugMessage("Timed out waiting for unload event; using fallback");
            return fallback;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            debugMessage("Interrupted while waiting for unload event");
            return fallback;
        } catch (ExecutionException e) {
            debugMessage("Error waiting for unload event: " + e.getMessage());
            return fallback;
        }
    }

    private void sendChat(Text text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            player.sendMessage(text, false);
        });
    }

}
