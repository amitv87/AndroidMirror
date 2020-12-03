#include <emscripten/bind.h>

using namespace emscripten;

struct H264Plane {
  uint8_t* data;
  int width, height, stride;

  H264Plane() : data(NULL), stride(0), height(0), width(0){}

  H264Plane(uint8_t* _data, int _width, int _height, int _stride) :
    data(_data), width(_width), height(_height), stride(_stride) {}

  val data_getter() const{
    return val(memory_view<uint8_t>(stride * height, data));
  }

  void data_setter(val v){}
};

struct H264Frame {
  H264Plane y, u, v;

  H264Frame(){}
  H264Frame(H264Plane _y, H264Plane _u, H264Plane _v) : y(_y), u(_u), v(_v) {}
};

class Decoder{
public:
  virtual int init() = 0;
  virtual H264Frame decode(std::string buffer) = 0;
};
