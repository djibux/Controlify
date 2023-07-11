package dev.isxander.controlify.ingame;

import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.controller.Controller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;

public class ControllerPlayerMovement extends Input {
    private final Controller<?, ?> controller;
    private final LocalPlayer player;

    public ControllerPlayerMovement(Controller<?, ?> controller, LocalPlayer player) {
        this.controller = controller;
        this.player = player;
    }

    @Override
    public void tick(boolean slowDown, float movementMultiplier) {
        if (Minecraft.getInstance().screen != null || player == null) {
            this.up = false;
            this.down = false;
            this.left = false;
            this.right = false;
            this.leftImpulse = 0;
            this.forwardImpulse = 0;
            this.jumping = false;
            this.shiftKeyDown = false;
            return;
        }

        var bindings = controller.bindings();

        this.forwardImpulse = bindings.WALK_FORWARD.state() - bindings.WALK_BACKWARD.state();
        this.leftImpulse = bindings.WALK_LEFT.state() - bindings.WALK_RIGHT.state();

        this.up = bindings.WALK_FORWARD.state() > 0;
        this.down = bindings.WALK_BACKWARD.state() > 0;
        this.left = bindings.WALK_LEFT.state() > 0;
        this.right = bindings.WALK_RIGHT.state() > 0;

        if (Controlify.instance().config().globalSettings().keyboardMovement) {
            this.forwardImpulse = Math.signum(this.forwardImpulse);
            this.leftImpulse = Math.signum(this.leftImpulse);
        }

        if (slowDown) {
            this.leftImpulse *= movementMultiplier;
            this.forwardImpulse *= movementMultiplier;
        }

        // this over-complication is so exiting a GUI with the button still held doesn't trigger a jump.
        if (bindings.JUMP.justPressed())
            this.jumping = true;
        if (!bindings.JUMP.held())
            this.jumping = false;

        if (player.getAbilities().flying || (player.isInWater() && !player.onGround()) || player.getVehicle() != null || !controller.config().toggleSneak) {
            if (bindings.SNEAK.justPressed())
                this.shiftKeyDown = true;
            if (!bindings.SNEAK.held())
                this.shiftKeyDown = false;
        } else {
            if (bindings.SNEAK.justPressed()) {
                this.shiftKeyDown = !this.shiftKeyDown;
            }
        }
    }

    public static void updatePlayerInput(@Nullable LocalPlayer player) {
        if (player == null)
            return;

        if (Controlify.instance().getCurrentController().isPresent()) {
            player.input = new DualInput(
                    new KeyboardInput(Minecraft.getInstance().options),
                    new ControllerPlayerMovement(Controlify.instance().getCurrentController().get(), player)
            );
        } else if (!(player.input instanceof KeyboardInput)) {
            player.input = new KeyboardInput(Minecraft.getInstance().options);
        }

    }
}
