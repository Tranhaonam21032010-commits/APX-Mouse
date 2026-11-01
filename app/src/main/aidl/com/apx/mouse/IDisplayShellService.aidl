package com.apx.mouse;

interface IDisplayShellService {
    void destroy();
    String inputInventory();
    String captureInputEvents(int seconds);
    String execute(String command);
}
