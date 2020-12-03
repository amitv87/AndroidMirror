#include "decoder_pre.h"

extern "C"{
  #include <libavcodec/avcodec.h>
  extern AVCodec ff_h264_decoder;
}

class H264Decoder : public Decoder {
  AVPacket packet;
  AVFrame *frame = NULL;
  AVCodecContext *codec_ctx = NULL;
public:
  int init(){
    if(codec_ctx) return 1;
    AVCodec *codec = &ff_h264_decoder;
    codec_ctx = avcodec_alloc_context3(codec);
    if(!codec_ctx) return -1;
    if(avcodec_open2(codec_ctx, codec, NULL) < 0) return -2;
    frame = av_frame_alloc();
    if(!frame) return -3;
    av_init_packet(&packet);
    return 0;
  }

  ~H264Decoder(){
    if(!codec_ctx) return;
    avcodec_close(codec_ctx);
    av_free(codec_ctx);
    av_frame_free(&frame);
  }

  H264Frame decode(std::string buffer){
    packet.size = buffer.length();
    packet.data = (uint8_t *)buffer.data();
    if(avcodec_send_packet(codec_ctx, &packet) || avcodec_receive_frame(codec_ctx, frame)) return H264Frame();
    int width = frame->width, height = frame->height;
    return H264Frame(
      H264Plane(frame->data[0], width, height, frame->linesize[0]),
      H264Plane(frame->data[1], width / 2, height / 2, frame->linesize[1]),
      H264Plane(frame->data[2], width / 2, height / 2, frame->linesize[2])
    );
  }
};

#include "decoder_post.h"
