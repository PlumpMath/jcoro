package org.jcoro;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.analysis.Frame;

import java.util.Set;

/**
 * @author elwood
 */
public class MethodAnalyzeResult {
    private final int restorePointCallsCount;
    private final Set<MethodId> restorePoints; // Can be null, if no restore points were found
    private final Set<MethodId> unpatchableRestorePoints; // Can be null, if no unpatchable restore points were found
    private final Frame[] frames;
    private final AbstractInsnNode[] insns;
    private final boolean rootLambda;

    public MethodAnalyzeResult(int restorePointCallsCount,
                               Set<MethodId> restorePoints,
                               Set<MethodId> unpatchableRestorePoints,
                               Frame[] frames,
                               AbstractInsnNode[] insns,
                               boolean rootLambda) {
        this.restorePointCallsCount = restorePointCallsCount;
        this.restorePoints = restorePoints;
        this.unpatchableRestorePoints = unpatchableRestorePoints;
        this.frames = frames;
        this.insns = insns;
        this.rootLambda = rootLambda;
    }

    /**
     * Количество мест вызовов обнаруженных в теле метода точек восстановления.
     * Необходимо при инструментировании кода, чтобы знать, на сколько точек ветвить switch.
     */
    public int getRestorePointCallsCount() {
        return restorePointCallsCount;
    }

    /**
     * Сигнатуры методов, вызовы которых были интерпретированы как вызовы точек восстановления.
     * Их может быть меньше restorePointCallsCount (если вызовов одних и тех же методов несколько).
     */
    public Set<MethodId> getRestorePoints() {
        return restorePoints;
    }

    /**
     * Сигнатуры методов-точек восстановления, которые ведут в unpatchable код
     * (код, который не будет инструментирован).
     */
    public Set<MethodId> getUnpatchableRestorePoints() {
        return unpatchableRestorePoints;
    }

    /**
     * Массив состояний фрейма. Размер массива равен количеству инструкций в теле метода.
     * Таким образом, для каждой инструкции есть состояние фрейма.
     */
    public Frame[] getFrames() {
        return frames;
    }

    /**
     * Массив инструкций. Размер массива равен размеру массива frames.
     * (Параллельные массивы).
     */
    public AbstractInsnNode[] getInsns() {
        return insns;
    }

    /**
     * True if lambda is considered as root lambda.
     * Root lambda should put on coro stack one extra item (null) when saving state
     * This is need to keep the coro stack balanced when resuming
     * (because resume() assumes ICoroRunnable.run() is always an instance method, but lambda method is not)
     */
    public boolean isRootLambda() {
        return rootLambda;
    }
}
