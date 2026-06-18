package com.tianhuiu.solvex.service;

import android.os.ParcelFileDescriptor;

interface IShizukuShellService {
    byte[] exec(in String[] command) = 1;
    int getSecureWindowCount() = 2;
    ParcelFileDescriptor execStream(in String[] command) = 3;
    void destroy() = 16777114;
}
