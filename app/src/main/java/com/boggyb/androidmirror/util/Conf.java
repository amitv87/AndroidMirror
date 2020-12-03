package com.boggyb.androidmirror.util;


import android.os.Parcel;
import android.os.Parcelable;

public class Conf implements Parcelable {
  public boolean isSecure = false;
  public boolean isAudioEnabled = false;
  public boolean isInputEnabled = false;
  public boolean isWebAccessEnabled = false;

  public Conf(){}

  private Conf(Parcel in) {
    isSecure = in.readByte() != 0;
    isAudioEnabled = in.readByte() != 0;
    isInputEnabled = in.readByte() != 0;
    isWebAccessEnabled = in.readByte() != 0;
  }

  public static final Creator<Conf> CREATOR = new Creator<Conf>() {
    @Override
    public Conf createFromParcel(Parcel in) {
      return new Conf(in);
    }

    @Override
    public Conf[] newArray(int size) {
      return new Conf[size];
    }
  };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeByte((byte) (isSecure ? 1 : 0));
    dest.writeByte((byte) (isAudioEnabled ? 1 : 0));
    dest.writeByte((byte) (isInputEnabled ? 1 : 0));
    dest.writeByte((byte) (isWebAccessEnabled ? 1 : 0));
  }
}
