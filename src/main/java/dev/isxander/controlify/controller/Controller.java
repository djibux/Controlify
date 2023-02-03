package dev.isxander.controlify.controller;

import dev.isxander.controlify.bindings.ControllerBindings;
import dev.isxander.controlify.bindings.ControllerTheme;
import dev.isxander.controlify.controller.hid.HIDIdentifier;
import dev.isxander.controlify.event.ControlifyEvents;
import org.hid4java.HidDevice;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class Controller {
    public static final Map<Integer, Controller> CONTROLLERS = new HashMap<>();
    public static final Controller DUMMY = new Controller(-1, "DUMMY", "DUMMY", false, "DUMMY", ControllerType.UNKNOWN);

    private final int joystickId;
    private final String guid;
    private final String name;
    private final boolean gamepad;
    private final String uid;
    private final ControllerType type;

    private ControllerState state = ControllerState.EMPTY;
    private ControllerState prevState = ControllerState.EMPTY;

    private final ControllerBindings bindings = new ControllerBindings(this);
    private ControllerConfig config = new ControllerConfig();

    public Controller(int joystickId, String guid, String name, boolean gamepad, String uid, ControllerType type) {
        this.joystickId = joystickId;
        this.guid = guid;
        this.name = name;
        this.gamepad = gamepad;
        this.uid = uid;
        this.type = type;
    }

    public ControllerState state() {
        return state;
    }

    public ControllerState prevState() {
        return prevState;
    }

    public void updateState() {
        if (!connected()) {
            state = prevState = ControllerState.EMPTY;
            return;
        }

        prevState = state;

        AxesState axesState = AxesState.fromController(this)
                .leftJoystickDeadZone(config().leftStickDeadzone, config().leftStickDeadzone)
                .rightJoystickDeadZone(config().rightStickDeadzone, config().rightStickDeadzone)
                .leftTriggerDeadZone(config().leftTriggerDeadzone)
                .rightTriggerDeadZone(config().rightTriggerDeadzone);
        ButtonState buttonState = ButtonState.fromController(this);
        state = new ControllerState(axesState, buttonState);

        ControlifyEvents.CONTROLLER_STATE_UPDATED.invoker().onControllerStateUpdate(this);
    }

    public ControllerBindings bindings() {
        return bindings;
    }

    public boolean connected() {
        return GLFW.glfwJoystickPresent(joystickId);
    }

    GLFWGamepadState getGamepadState() {
        GLFWGamepadState state = GLFWGamepadState.create();
        if (gamepad)
            GLFW.glfwGetGamepadState(joystickId, state);
        return state;
    }

    public int id() {
        return joystickId;
    }

    public String guid() {
        return guid;
    }

    public String uid() {
        return uid;
    }

    public ControllerType type() {
        return type;
    }

    public String name() {
        if (config().customName != null)
            return config().customName;
        return name;
    }

    public boolean gamepad() {
        return gamepad;
    }

    public ControllerConfig config() {
        return config;
    }

    public void setConfig(ControllerConfig config) {
        this.config = config;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Controller) obj;
        return Objects.equals(this.guid, that.guid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(guid);
    }

    public static Controller create(int id, HidDevice device) {
        if (id > GLFW.GLFW_JOYSTICK_LAST)
            throw new IllegalArgumentException("Invalid joystick id: " + id);
        if (CONTROLLERS.containsKey(id))
            return CONTROLLERS.get(id);

        String guid = GLFW.glfwGetJoystickGUID(id);
        boolean gamepad = GLFW.glfwJoystickIsGamepad(id);
        String fallbackName = gamepad ? GLFW.glfwGetGamepadName(id) : GLFW.glfwGetJoystickName(id);
        String uid = device.getPath();
        ControllerType type = ControllerType.getTypeForHID(new HIDIdentifier(device.getVendorId(), device.getProductId()));
        String name = type != ControllerType.UNKNOWN || fallbackName == null ? type.friendlyName() : fallbackName;
        int tries = 1;
        while (CONTROLLERS.values().stream().map(Controller::name).anyMatch(name::equals)) {
            name = type.friendlyName() + " (" + tries++ + ")";
        }

        Controller controller = new Controller(id, guid, name, gamepad, uid, type);
        CONTROLLERS.put(id, controller);

        return controller;
    }

}
