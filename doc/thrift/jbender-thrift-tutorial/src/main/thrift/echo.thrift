namespace java echo.thrift

struct EchoRequest {
    1: optional string message;
}

struct EchoResponse {
    2: optional string message;
}

service EchoService {
    EchoResponse echo(1: EchoRequest request);
}
