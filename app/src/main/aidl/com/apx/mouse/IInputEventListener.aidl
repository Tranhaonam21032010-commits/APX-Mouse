package com.apx.mouse;

oneway interface IInputEventListener {
    void onInputEventBatch(in String[] lines);
    void onInputCaptureEnded(String message);
}
