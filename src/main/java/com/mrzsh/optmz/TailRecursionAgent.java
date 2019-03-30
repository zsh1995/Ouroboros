package com.mrzsh.optmz;

import java.lang.instrument.Instrumentation;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TailRecursionAgent {

    private static final Pattern pattern = Pattern.compile("-d\\s+(?<debug>y|n)(?:\\s+-l\\s+(?<log>\\S+))?(?:\\s+-o\\s+(?<output>\\S+))?");

    public static void premain(String args, Instrumentation instrumentation) {
        Matcher matcher;
        if(args != null && (matcher = pattern.matcher(args)).matches()){
            boolean debug = "y".equals(matcher.group(1));
            TailRecursionTransformer.Builder builder = new TailRecursionTransformer.Builder(debug);
            String log;
            if( (log = matcher.group(2)) != null) {
                builder.log(log);
            }
            String output;
            if((output = matcher.group(3)) != null) {
                builder.outputModifiedClass(output);
            }
            instrumentation.addTransformer(builder.build());
        } else {
          instrumentation.addTransformer(new TailRecursionTransformer.Builder(false)
                  .build());
        }
    }

    public static void main(String[] args) {

    }
}
