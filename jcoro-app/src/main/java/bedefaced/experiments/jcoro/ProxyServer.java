package bedefaced.experiments.jcoro;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;

import org.jcoro.Async;
import org.jcoro.Await;
import org.jcoro.Coro;
import org.jcoro.ICoroRunnable;
import org.jcoro.nio.ServerSocketChannel;
import org.jcoro.nio.SocketChannel;

public class ProxyServer implements Runnable {

    public InetAddress hostAddress;
    public int portlocal;
    public InetAddress remoteAddress;
    public int portremote;
    public AsynchronousServerSocketChannel serverChannel;

    public ProxyServer(InetAddress hostAddress, int portlocal,
                       String remotehost, int portremote) throws UnknownHostException {
        this.hostAddress = hostAddress;
        this.portlocal = portlocal;

        this.portremote = portremote;
        this.remoteAddress = InetAddress.getByName(remotehost);
    }

    public static void main(String[] args) throws NumberFormatException,
            UnknownHostException {
        if (args.length != 3) {
            System.err.println("usage: <port> <host> <port>");
            return;
        }
        new ProxyServer(null, Integer.valueOf(args[0]), args[1],
            Integer.valueOf(args[2])).run();
    }

    @Override
    public void run() {
        try {
            serverChannel = AsynchronousServerSocketChannel.open();

            InetSocketAddress isa = new InetSocketAddress(this.hostAddress,
                    this.portlocal);
            serverChannel.bind(isa);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        ByteBuffer readClientBuffer = ByteBuffer.allocate(32);
        ByteBuffer readRemoteBuffer = ByteBuffer.allocate(32);

        Coro acceptClientCoro = Coro.initSuspended(new ICoroRunnable() {
            @Override
            @Async({@Await("accept"), @Await("connect")})
            public void run() {
                while (true) {
                    AsynchronousSocketChannel client = ServerSocketChannel
                            .accept(serverChannel);

                    AsynchronousSocketChannel remote = null;
                    try {
                        remote = AsynchronousSocketChannel.open();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    final AsynchronousSocketChannel finalRemote = remote;

                    SocketChannel.connect(remote, new InetSocketAddress(
                            remoteAddress, portremote));

                    Coro readClientCoro = Coro
                            .initSuspended(new ICoroRunnable() {

                                @Override
                                @Async({@Await("read"), @Await("write")})
                                public void run() {
                                    while (true) {
                                        SocketChannel.read(client,
                                                readClientBuffer);
                                        readClientBuffer.flip();
                                        SocketChannel.write(finalRemote,
                                                readClientBuffer);
                                    }
                                }

                            });

                    Coro readRemoteCoro = Coro
                            .initSuspended(new ICoroRunnable() {

                                @Override
                                @Async({@Await("read"), @Await("write")})
                                public void run() {
                                    while (true) {
                                        SocketChannel.read(finalRemote,
                                                readRemoteBuffer);
                                        readRemoteBuffer.flip();
                                        SocketChannel.write(client,
                                                readRemoteBuffer);
                                    }
                                }

                            });

                    readClientCoro.start();
                    readRemoteCoro.start();

                }
            }
        });

        acceptClientCoro.start();
    }

}
