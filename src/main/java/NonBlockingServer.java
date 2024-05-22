import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class NonBlockingServer {

    private Map<SocketChannel, List<byte[]>> keepDataTrack = new HashMap<>();
    private ByteBuffer buffer = ByteBuffer.allocate(2 * 1024);

    private void startEchoServer() {
        try ( // 1
                Selector selector = Selector.open(); // 2
                ServerSocketChannel serverSocketChannel = ServerSocketChannel.open(); // 3
        ) {
            if ((serverSocketChannel.isOpen()) && (selector.isOpen())) { // 4
                serverSocketChannel.configureBlocking(false); // 5
                serverSocketChannel.bind(new InetSocketAddress(8888)); // 6

                serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT); // 7
                System.out.println("접속 대기중");

                while (true) {
                    selector.select(); //8
                    Iterator<SelectionKey> keys = selector.selectedKeys().iterator(); // 9

                    while (keys.hasNext()) {
                        SelectionKey key = keys.next();
                        keys.remove(); // 10

                        if (!key.isValid()) {
                            continue;
                        }

                        if (key.isAcceptable()) { // 11
                            this.acceptOP(key, selector);
                        } else if (key.isReadable()) { // 12
                            this.readOP(key);
                        } else if (key.isWritable()) { // 13
                            this.writeOP(key);
                        }
                    }
                }
            } else {
                System.out.println("서버 소켓을 생성하지 못했습니다.");
            }
        } catch (IOException ex) {
            System.err.println(ex);
        }
    }

    private void acceptOP(SelectionKey key, Selector selector) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel(); // 14
        SocketChannel socketChannel = serverChannel.accept(); // 15
        socketChannel.configureBlocking(false); // 16

        System.out.println("클라이언트 연결됨 : " + socketChannel.getRemoteAddress());

        keepDataTrack.put(socketChannel, new ArrayList<byte[]>());
        socketChannel.register(selector, SelectionKey.OP_READ); // 17
    }

    private void readOP(SelectionKey key) {
        try {
            SocketChannel socketChannel = (SocketChannel) key.channel();
            buffer.clear();
            int numRead = -1;
            try {
                numRead = socketChannel.read(buffer);
            } catch (IOException e) {
                System.err.println("데이터 읽기 에러!");
            }

            if (numRead == -1) {
                this.keepDataTrack.remove(socketChannel);
                System.out.println("클라이언트 연결 종료 : " + socketChannel.getRemoteAddress());
                socketChannel.close();
                key.cancel();
                return;
            }

            byte[] data = new byte[numRead];
            System.arraycopy(buffer.array(), 0, data, 0, numRead);
            System.out.println(new String(data, "UTF-8") + " from " + socketChannel.getRemoteAddress());

            doEchoJob(key, data);
        } catch (IOException ex) {
            System.err.println(ex);
        }
    }


    private void writeOP(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        List<byte[]> channelData = keepDataTrack.get(socketChannel);
        Iterator<byte[]> its = channelData.iterator();

        while (its.hasNext()) {
            byte[] it = its.next();
            its.remove();
            socketChannel.write(ByteBuffer.wrap(it));
        }

        key.interestOps(SelectionKey.OP_READ);
    }

    private void doEchoJob(SelectionKey key, byte[] data) {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        List<byte[]> channelData = keepDataTrack.get(socketChannel);
        channelData.add(data);

        key.interestOps(SelectionKey.OP_WRITE);
    }

}
