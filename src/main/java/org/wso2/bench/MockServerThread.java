package org.wso2.bench;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderUtil;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;

import java.util.concurrent.DelayQueue;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class MockServerThread extends Thread {

    private final DelayQueue<DelayedElement>  queue = new DelayQueue<DelayedElement>();

    public void run() {
        DelayedElement elem = null;

        while(true) {
            try {
                elem = (DelayedElement) queue.take();
                beginResponse(elem.getContext(), elem.getTrailer(), elem.getBuf(), elem.getRequest(), elem.getResponseContentType());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    public void delayEvent(ChannelHandlerContext ctx, LastHttpContent trailer, HttpRequest request, StringBuilder buf, int delay, String responseContentType, int id) {
        DelayedElement delayedElement = new DelayedElement(ctx, trailer, request, HardcodedResponse.BYTE_RESPONSE, System.currentTimeMillis(), delay, responseContentType);
        queue.add(delayedElement);
    }

    private void beginResponse(ChannelHandlerContext ctx, LastHttpContent currentObject, byte[] buf, HttpRequest request, String responseContentType) {
        writeResponse(currentObject, ctx, request, buf, responseContentType);
    }

    private static void send100Continue(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, CONTINUE);
        ChannelFuture future = ctx.channel().write(response);
        future.addListener(ChannelFutureListener.CLOSE);
    }

    private void writeResponse(HttpObject currentObj, ChannelHandlerContext ctx, HttpMessage request, byte[] buf, String responseContentType) {
        // Decide whether to close the connection or not.
        boolean keepAlive = HttpHeaderUtil.isKeepAlive(request);
        // Build the response object.
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, currentObj.decoderResult().isSuccess()? OK : BAD_REQUEST,
                //Unpooled.copiedBuffer(buf.toString(), CharsetUtil.UTF_8));
                Unpooled.copiedBuffer(buf));

        response.headers().set(CONTENT_TYPE, responseContentType);

        if (keepAlive) {
            // Add 'Content-Length' header only for a keep-alive connection.
            response.headers().set(CONTENT_LENGTH, String.valueOf(response.content().readableBytes()));
            // Add keep alive header as per:
            // - http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
            response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        }

        ChannelFuture future = ctx.channel().write(response);

        if(!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }

        ctx.channel().flush();

    }

}
