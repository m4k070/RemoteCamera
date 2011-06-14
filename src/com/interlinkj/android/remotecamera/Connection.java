package com.interlinkj.android.remotecamera;

import java.util.Set;

/**
 * 外部接続管理用抽象クラス
 * @author Ito
 *
 */
public abstract class Connection {

	/**
	 * 接続状態
	 * @return true:接続中 false:未接続 
	 */
	public abstract boolean isConnecting();

	/**
	 * 接続開始
	 * @param address
	 * 	接続先のアドレス
	 */
	public abstract void connect(String address);

	/**
	 * 設定済みの接続先を取得
	 * @return 接続先のSet
	 */
	public abstract Set<String> getDeviceNameSet();
	
	/**
	 * デバイス名から接続用のアドレスを取得
	 * @param name
	 *  デバイス名
	 * @return
	 */
	public abstract String getDeviceAddress(String name);

	/**
	 * 接続先からデータを読み込む
	 * @param aBuf
	 * 	バッファ
	 * @return 読み込んだバイト数
	 */
	public abstract int read(byte[] aBuf);

	/**
	 * 接続先へデータを書き込む
	 * @param aBuf バッファ
	 */
	public abstract void write(byte[] aBuf);

	/**
	 * 切断処理
	 */
	public abstract void close();
}