package com.apx.mouse;

oneway interface IInputEventListener {
    void onInputEventBatch(in String[] lines);
    void onNativeInputEventBatch(in int[] eventTuples);
    void onInputCaptureEnded(String message);
}
