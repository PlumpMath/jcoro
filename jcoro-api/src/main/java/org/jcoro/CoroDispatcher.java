package org.jcoro;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author elwood
 */
public class CoroDispatcher {

    static {
        executorService = Executors.newFixedThreadPool(5);
    }

    private static ExecutorService executorService;

    /**
     * ��������� ������ � ���������, ����������� ������������� ����������
     * (����� � �������������) � ����� �� ������� ����. ���� ������ ������ ���-�� ������ yield(),
     * ����� ����� ����� ������� resume(). �� ������ ����� ������������ ���������� ��� �� ������� ������.
     */
    public static void post(Coro coro) {
        executorService.execute(() -> {
            coro.resume();
        });
    }

    /**
     * ��������� ������ � ������� ������, ���������� ��������� Coro (������� ���������� ����������
     * ��� ������ yield ������ ������, ��� ��� ������ � ����������). ���� ����������� �� ���� ���������
     * ��������� (��� ������ yield), �� � ���������� ����� �����������, ������ coro.resume();
     */
//    public static Coro run(ICoroRunnable runnable) {
//        Coro.getOrCreate()
//        runnable
//    }
}
