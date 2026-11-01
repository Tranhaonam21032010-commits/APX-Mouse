package com.apx.mouse;

interface IDisplayShellService {
    void destroy();
    String inputInventory();
    String captureInputEvents(int seconds);
    String execute(String command);
    String probeTouchInjection();
    String injectTouchFrame(int action, in int[] pointerIds, in float[] xs, in float[] ys, long downTime, long eventTime);
}
