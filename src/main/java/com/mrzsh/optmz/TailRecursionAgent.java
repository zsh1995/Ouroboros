package com.mrzsh.optmz;

import java.lang.instrument.Instrumentation;

public class TailRecursionAgent {

    public static void premain(String args, Instrumentation instrumentation) {
        instrumentation.addTransformer(new TailRecursionTransformer());
    }

    public static void main(String[] args) {

    }
}
