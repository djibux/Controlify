package dev.isxander.controlify;

import com.mojang.logging.LogUtils;
import dev.isxander.controlify.compatibility.screen.ScreenProcessorProvider;
import dev.isxander.controlify.config.ControlifyConfig;
import dev.isxander.controlify.controller.Controller;
import dev.isxander.controlify.controller.ControllerState;
import dev.isxander.controlify.controller.hid.ControllerHIDService;
import dev.isxander.controlify.event.ControlifyEvents;
import dev.isxander.controlify.ingame.InGameInputHandler;
import dev.isxander.controlify.mixins.feature.virtualmouse.MouseHandlerAccessor;
import dev.isxander.controlify.virtualmouse.VirtualMouseHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

public class Controlify {
    public static final Logger LOGGER = LogUtils.getLogger();
    private static Controlify instance = null;

    private Controller currentController;
    private InGameInputHandler inGameInputHandler;
    private VirtualMouseHandler virtualMouseHandler;
    private InputMode currentInputMode;
    private ControllerHIDService controllerHIDService;

    private final ControlifyConfig config = new ControlifyConfig();

    public void onInitializeInput() {
        Minecraft minecraft = Minecraft.getInstance();

        inGameInputHandler = new InGameInputHandler(Controller.DUMMY); // initialize with dummy controller before connection in case of no controllers
        controllerHIDService = new ControllerHIDService();

        // find already connected controllers
        for (int i = 0; i < GLFW.GLFW_JOYSTICK_LAST; i++) {
            if (GLFW.glfwJoystickPresent(i)) {
                int jid = i;
                controllerHIDService.awaitNextDevice(device -> {
                    setCurrentController(Controller.create(jid, device));
                    LOGGER.info("Controller found: " + currentController.name());
                });
            }
        }

        controllerHIDService.start();

        // load after initial controller discovery
        config().load();

        // listen for new controllers
        GLFW.glfwSetJoystickCallback((jid, event) -> {
            if (event == GLFW.GLFW_CONNECTED) {
                controllerHIDService.awaitNextDevice(device -> {
                    setCurrentController(Controller.create(jid, device));
                    LOGGER.info("Controller connected: " + currentController.name() + " (" + device.getPath() + ")");
                    this.setCurrentInputMode(InputMode.CONTROLLER);

                    config().load(); // load config again if a configuration already exists for this controller
                    config().save(); // save config if it doesn't exist

                    minecraft.getToasts().addToast(SystemToast.multiline(
                            minecraft,
                            SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                            Component.translatable("controlify.toast.controller_connected.title"),
                            Component.translatable("controlify.toast.controller_connected.description")
                    ));
                });

            } else if (event == GLFW.GLFW_DISCONNECTED) {
                var controller = Controller.CONTROLLERS.remove(jid);
                if (controller != null) {
                    setCurrentController(Controller.CONTROLLERS.values().stream().filter(Controller::connected).findFirst().orElse(null));
                    LOGGER.info("Controller disconnected: " + controller.name());
                    this.setCurrentInputMode(currentController == null ? InputMode.KEYBOARD_MOUSE : InputMode.CONTROLLER);

                    minecraft.getToasts().addToast(SystemToast.multiline(
                            minecraft,
                            SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                            Component.translatable("controlify.toast.controller_disconnected.title"),
                            Component.translatable("controlify.toast.controller_disconnected.description", controller.name())
                    ));
                }
            }
        });

        this.virtualMouseHandler = new VirtualMouseHandler();

        ClientTickEvents.START_CLIENT_TICK.register(this::tick);
    }

    public void tick(Minecraft client) {
        for (Controller controller : Controller.CONTROLLERS.values()) {
            controller.updateState();
        }

        ControllerState state = currentController == null ? ControllerState.EMPTY : currentController.state();

        if (state.hasAnyInput())
            this.setCurrentInputMode(InputMode.CONTROLLER);

        if (currentController == null) {
            this.setCurrentInputMode(InputMode.KEYBOARD_MOUSE);
            return;
        }

        if (client.screen != null) {
            if (!this.virtualMouseHandler().isVirtualMouseEnabled())
                ScreenProcessorProvider.provide(client.screen).onControllerUpdate(currentController);
        } else {
            this.inGameInputHandler().inputTick();
        }
        this.virtualMouseHandler().handleControllerInput(currentController);
    }

    public ControlifyConfig config() {
        return config;
    }

    public Controller currentController() {
        return currentController;
    }

    public void setCurrentController(Controller controller) {
        if (this.currentController == controller) return;
        this.currentController = controller;

        this.inGameInputHandler = new InGameInputHandler(this.currentController != null ? controller : Controller.DUMMY);
    }

    public InGameInputHandler inGameInputHandler() {
        return inGameInputHandler;
    }

    public VirtualMouseHandler virtualMouseHandler() {
        return virtualMouseHandler;
    }

    public InputMode currentInputMode() {
        return currentInputMode;
    }

    public void setCurrentInputMode(InputMode currentInputMode) {
        if (this.currentInputMode == currentInputMode) return;
        this.currentInputMode = currentInputMode;

        var minecraft = Minecraft.getInstance();
        hideMouse(currentInputMode == InputMode.CONTROLLER);
        if (minecraft.screen != null) {
            ScreenProcessorProvider.provide(minecraft.screen).onInputModeChanged(currentInputMode);
        }

        ControlifyEvents.INPUT_MODE_CHANGED.invoker().onInputModeChanged(currentInputMode);
    }

    public void hideMouse(boolean hide) {
        var minecraft = Minecraft.getInstance();
        GLFW.glfwSetInputMode(
                minecraft.getWindow().getWindow(),
                GLFW.GLFW_CURSOR,
                hide
                        ? GLFW.GLFW_CURSOR_HIDDEN
                        : GLFW.GLFW_CURSOR_NORMAL
        );
        if (minecraft.screen != null) {
            var mouseHandlerAccessor = (MouseHandlerAccessor) minecraft.mouseHandler;
            if (hide && !virtualMouseHandler().isVirtualMouseEnabled()) {
                // stop mouse hovering over last element before hiding cursor but don't actually move it
                // so when the user switches back to mouse it will be in the same place
                mouseHandlerAccessor.invokeOnMove(minecraft.getWindow().getWindow(), 0, 0);
            }
        }
    }

    public static Controlify instance() {
        if (instance == null) instance = new Controlify();
        return instance;
    }
}
