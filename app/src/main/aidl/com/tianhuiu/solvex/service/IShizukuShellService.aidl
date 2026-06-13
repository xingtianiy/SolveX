package com.tianhuiu.solvex.service;

interface IShizukuShellService {
    byte[] exec(in String[] command) = 1;
    int getSecureWindowCount() = 2;
    void destroy() = 16777114;
}
