package org.runnerup.wear

import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.PutDataRequest.WEAR_URI_SCHEME
import com.google.android.gms.wearable.Wearable
import java.util.function.Consumer

class WearableClient(context: Context) {
  private val dataClient: DataClient = Wearable.getDataClient(context)

  fun readData(path: String, consumer: Consumer<DataItem?>) {
    dataClient
      .getDataItems(Uri.Builder().scheme(WEAR_URI_SCHEME).path(path).build())
      .addOnCompleteListener { task ->
        if (task.isSuccessful) {
          val dataItems = task.result
          if (dataItems.count == 0) {
            consumer.accept(null)
          } else {
            for (dataItem in dataItems) {
              consumer.accept(dataItem)
            }
          }
          dataItems.release()
        } else {
          println("task.getException(): " + task.exception)
        }
      }
  }

  fun putData(path: String): Task<DataItem> = dataClient.putDataItem(PutDataRequest.create(path))

  fun putData(path: String, b: Bundle): Task<DataItem> =
    dataClient.putDataItem(PutDataRequest.create(path).setData(DataMap.fromBundle(b).toByteArray()))

  fun deleteData(path: String): Task<Int> =
    dataClient.deleteDataItems(Uri.Builder().scheme(WEAR_URI_SCHEME).path(path).build())
}
