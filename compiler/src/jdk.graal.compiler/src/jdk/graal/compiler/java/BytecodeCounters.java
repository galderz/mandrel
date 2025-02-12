package jdk.graal.compiler.java;

import jdk.graal.compiler.bytecode.Bytecode;
import jdk.graal.compiler.bytecode.Bytecodes;

import java.util.Arrays;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.IntStream;

public enum BytecodeCounters {
    INSTANCE;

    final LongAdder[] bcCounters = new LongAdder[Bytecodes.END];

    BytecodeCounters() {
        for (int i = 0; i < bcCounters.length; i++) {
            bcCounters[i] = new LongAdder();
        }
    }

    void increment(int opcode)
    {
        bcCounters[opcode].increment();
    }

    public void print()
    {
        System.out.println(Arrays.toString(bcCounters));

        long totalSum = Arrays.stream(bcCounters)
                .mapToLong(LongAdder::longValue)
                .sum();

        final int[] sortedBytecodes = IntStream.range(0, bcCounters.length)
            .boxed()
            .sorted((i, j) -> Long.compare(bcCounters[j].longValue(), bcCounters[i].longValue()))
            .mapToInt(i -> i)
            .toArray();

        int limit = 5;
        for (int i = 0; i < limit; i++) {
            final int bytecode = sortedBytecodes[i];
            System.out.printf(
                "%d = %d (%.2f%%)%n"
                , bytecode
                , bcCounters[bytecode].longValue()
                , ((double) bcCounters[bytecode].longValue() / totalSum) * 100
            );
        }
    }
}
