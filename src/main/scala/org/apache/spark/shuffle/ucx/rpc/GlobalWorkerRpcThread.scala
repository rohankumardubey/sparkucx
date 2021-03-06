/*
* Copyright (C) Mellanox Technologies Ltd. 2019. ALL RIGHTS RESERVED.
* See file LICENSE for terms.
*/
package org.apache.spark.shuffle.ucx.rpc

import org.openucx.jucx.ucp.{UcpAmData, UcpConstants, UcpEndpoint, UcpWorker}
import org.openucx.jucx.ucs.UcsConstants
import org.apache.spark.internal.Logging
import org.apache.spark.shuffle.ucx.UcxShuffleTransport
import org.apache.spark.shuffle.utils.UnsafeUtils
import org.apache.spark.util.ThreadUtils

class GlobalWorkerRpcThread(globalWorker: UcpWorker, transport: UcxShuffleTransport)
  extends Thread with Logging {
  setDaemon(true)
  setName("Global worker progress thread")

  globalWorker.setAmRecvHandler(0, (headerAddress: Long, headerSize: Long, amData: UcpAmData,
                                     replyEp: UcpEndpoint) => {

    val replyTag = UnsafeUtils.getByteBufferView(headerAddress, headerSize.toInt).getInt
    val data = UnsafeUtils.getByteBufferView(amData.getDataAddress, amData.getLength.toInt)
    transport.handleFetchBlockRequest(replyTag, data, replyEp)
    UcsConstants.STATUS.UCS_OK
  })

  override def run(): Unit = {
    if (transport.ucxShuffleConf.useWakeup) {
      while (!isInterrupted) {
        if (globalWorker.progress() == 0) {
          globalWorker.waitForEvents()
        }
      }
    } else {
      while (!isInterrupted) {
        globalWorker.progress()
      }
    }
  }
}
