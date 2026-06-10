package com.tianhuiu.solvex.service;

interface IShizukuShellService {
    byte[] exec(in String[] command) = 1;
    void destroy() = 16777114;
}
