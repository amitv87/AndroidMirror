#include "codec_api.h"
#include "decoder_pre.h"

class H264Decoder : public Decoder {
  uint8_t *pDst[3];
  SBufferInfo sDstInfo;
  SSysMEMBuffer* b = &sDstInfo.UsrData.sSystemBuffer;
  ISVCDecoder *mDecoder = NULL;
public:
  int init(){
    if(mDecoder) return 1;
    int rc = WelsCreateDecoder(&mDecoder);
    if(rc) return rc;
    SDecodingParam s = {0};
    s.uiTargetDqLayer = (uint8_t) - 1;
    s.sVideoProperty.eVideoBsType = VIDEO_BITSTREAM_SVC;
    rc = mDecoder->Initialize(&s);
    return rc;
  }

  ~H264Decoder(){
    WelsDestroyDecoder(mDecoder);
    mDecoder = NULL;
  }

  H264Frame decode(std::string buffer){
    int ret = mDecoder->DecodeFrameNoDelay((uint8_t*)buffer.data(), buffer.length(), &pDst[0], &sDstInfo);
    if(ret != dsErrorFree || sDstInfo.iBufferStatus != 1) return H264Frame();

    return H264Frame(
      H264Plane(pDst[0], b->iWidth, b->iHeight, b->iStride[0]),
      H264Plane(pDst[1], b->iWidth / 2, b->iHeight / 2, b->iStride[1]),
      H264Plane(pDst[2], b->iWidth / 2, b->iHeight / 2, b->iStride[1])
    );
  }
};

#include "decoder_post.h"
