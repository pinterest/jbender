package echo.server;

import co.paralleluniverse.fibers.Suspendable;
import com.pinterest.quasar.thrift.TFiberServer;
import com.pinterest.quasar.thrift.TFiberServerSocket;
import echo.thrift.EchoRequest;
import echo.thrift.EchoResponse;
import echo.thrift.EchoService;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TFastFramedTransport;
import java.net.InetSocketAddress;

public class Main {
    static final class EchoServiceImpl implements EchoService.Iface {
        @Override
        @Suspendable
        public EchoResponse echo(EchoRequest request) throws TException {
            return new EchoResponse().setMessage(request.getMessage());
        }
    }

    @Suspendable
    public static void main(String[] args) throws Exception {
        EchoService.Processor<EchoService.Iface> processor =
            new EchoService.Processor<EchoService.Iface>(new EchoServiceImpl());
        TFiberServerSocket trans = new TFiberServerSocket(new InetSocketAddress(9999));
        TFiberServer.Args targs = new TFiberServer.Args(trans, processor)
            .protocolFactory(new TBinaryProtocol.Factory())
            .transportFactory(new TFastFramedTransport.Factory());
        TFiberServer server = new TFiberServer(targs);
        server.serve();
        server.join();
    }
}
