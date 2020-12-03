EMSCRIPTEN_BINDINGS(){

  #ifdef EM_INIT
  EM_INIT();
  #endif

  value_object<H264Plane>("H264Plane")
    .field("data", &H264Plane::data_getter, &H264Plane::data_setter)
    .field("stride", &H264Plane::stride)
    .field("height", &H264Plane::height)
    .field("width", &H264Plane::width)
  ;

  value_object<H264Frame>("H264Frame")
    .field("y", &H264Frame::y)
    .field("u", &H264Frame::u)
    .field("v", &H264Frame::v)
  ;

  class_<H264Decoder>("H264Decoder")
    .smart_ptr_constructor("H264DecoderPtr", &std::make_shared<H264Decoder>)
    .function("init", &H264Decoder::init)
    .function("decode", &H264Decoder::decode)
  ;
}
