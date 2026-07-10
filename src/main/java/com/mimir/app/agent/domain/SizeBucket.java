package com.mimir.app.agent.domain;

import lombok.Data;

@Data
public class SizeBucket<T> {
    private T xs;
    private T s;
    private T m;
    private T l;
    private T xl;
    private T xxl;
    private T size26;
    private T size28;
    private T size30;
    private T size32;
    private T size34;
    private T size36;
    private T size38;
    private T size40;
    private T size42;
    private T total;
}
