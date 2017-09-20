package com.advancedtelematic.director.http

import com.advancedtelematic.director.util.{DirectorSpec, DefaultPatience, ResourceSpec}

class CancelUpdateSpec extends DirectorSpec
    with DefaultPatience
    with DeviceRegistrationUtils
    with ResourceSpec
    with NamespacedRequests {

  testWithNamespace("can cancel update") { implicit ns =>
    val (device, primEcu, ecus) = createDeviceWithImages(afn, bfn)

    setRandomTargets(device, primEcu+:ecus)

    deviceQueueOk(device).length shouldBe 1

    cancelDeviceOk(device)

    deviceQueueOk(device).length shouldBe 0
  }

  testWithNamespace(s"can only cancel if update is not inflight") { implicit ns =>
    createRepo
    val (device, primEcu, ecus) = createDeviceWithImages(afn, bfn)

    setRandomTargets(device, primEcu+:ecus, None)

    deviceQueueOk(device).length shouldBe 1

    //make it inflight
    val t = fetchTargetsFor(device)
    t.signed.version shouldBe 1

    cancelDeviceFail(device)

    deviceQueueOk(device).length shouldBe 1
  }

  testWithNamespace("cancel several devices") { implicit ns =>
    createRepo
    val (device1, primEcu1, ecus1) = createDeviceWithImages(afn, bfn)
    val (device2, primEcu2, ecus2) = createDeviceWithImages(afn, bfn)

    setRandomTargets(device1, primEcu1+:ecus1, None)
    setRandomTargets(device2, primEcu2+:ecus2)

    deviceQueueOk(device1).length shouldBe 1
    deviceQueueOk(device2).length shouldBe 1

    //make device1 inflight
    val t = fetchTargetsFor(device1)
    t.signed.version shouldBe 1

    cancelDevices(device1, device2) shouldBe Seq(device2)

    deviceQueueOk(device1).length shouldBe 1
    deviceQueueOk(device2).length shouldBe 0
  }
}