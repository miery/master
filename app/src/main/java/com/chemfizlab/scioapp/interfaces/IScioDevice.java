package com.chemfizlab.scioapp.interfaces;

/**
 * Created by nadavg on 19/07/2016.
 */
public interface IScioDevice {
    void onScioButtonClicked();
    void onScioConnected();
    void onScioConnectionFailed();
    void onScioDisconnected();
}
