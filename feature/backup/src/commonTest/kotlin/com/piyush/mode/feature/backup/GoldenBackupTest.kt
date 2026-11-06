package com.piyush.mode.feature.backup

import com.piyush.mode.feature.backup.domain.BackupException
import com.piyush.mode.feature.backup.domain.BackupFailure
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A locked backup made once on the JVM (the JDK provider), kept byte for byte. Run on the iOS simulator this
 * proves CryptoKit and CommonCrypto open what the JDK sealed; on the JVM, that the format hasn't drifted.
 */
class GoldenBackupTest {
    @Test
    fun aBackupMadeElsewhereStillOpens() = runTest {
        val b = Phone()
        b.files.files["/tmp/golden"] = Base64.decode(GOLDEN)
        assertEquals(BackupFailure.WrongPassword, assertFailsWith<BackupException> { b.service.restore("/tmp/golden", "correct horsf") }.failure)
        val report = b.service.restore("/tmp/golden", "correct horse")
        assertEquals(listOf("OpenRouter"), report.restoredSignIns)
        assertEquals("sk-or-golden", b.secrets.get("openrouter_api_key"))
        assertEquals("Send Asha good morning", b.rows().turns.single().text)
        assertEquals("Asha", b.rows().conversations.single().title)
    }

    private companion object {
        const val GOLDEN =
            "TU9ERS1CQUNLVVAKeyJmb3JtYXQiOiJtb2RlLWJhY2t1cCIsInZlcnNpb24iOjEsImNyZWF0ZWRBdE1zIjoxNzkzOTgyMDAwMDAw" +
            "LCJkZXZpY2VOYW1lIjoiUGl5dXNo4oCZcyBpUGhvbmUiLCJhcHBWZXJzaW9uIjoiMS40IiwiY291bnRzIjp7ImNoYXRzIjoxLCJt" +
            "ZXNzYWdlcyI6MSwibWVtb3JpZXMiOjAsInRhc2tzIjowLCJyZXBlYXRzIjowLCJydW5zIjowfSwiaGFzU2lnbklucyI6dHJ1ZSwi" +
            "c2lnbkluTmFtZXMiOlsiT3BlblJvdXRlciJdLCJsb2NrZWQiOnRydWUsImxvY2siOnsia2RmIjoiUEJLREYyLUhNQUMtU0hBMjU2" +
            "IiwiaXRlcmF0aW9ucyI6MzEwMDAwLCJzYWx0IjoiaWlNeW15UXY3WnRybXFQb3lCalFBUT09IiwiY2lwaGVyIjoiQUVTLTI1Ni1H" +
            "Q00iLCJub25jZSI6Ik1oSHB5ZG12R0w3Z3plTUgiLCJjaGVjayI6ImlyeTdxK0xKMTUvaXc1bHlMRVlobXZjTlVnWk1SUjA5V0ox" +
            "TFM2NUxPcDQ9In19CiG3jiMyehkWILzhWop51o7ecWZh1BdUkVsp7xKmXLFrOK0uUKAL/xXbJ4wdX502QgmGT5uhKmdpqC/Qbqdy" +
            "rXJGIfUHL025FboiK9JUmSBlOlshpzzScrNpA5VmUAw16YaR/b9O8DoDHum3BGgcOJ52llEmshoIqIVtyQwzbXZU78WX2vjgNsHi" +
            "yf1uQb/dNkcEO0OBJLvzGNQ55XVZhAP69Bg/9GycQpqBFM1w6t52s4SdlxlZwNI2mNvn5SQw0HSTWost1WdduqpQC3p/RbOZ/1JG" +
            "YRa7caRT4bV0Ejkbfdly3FDC3u37CiqhMXCsTuNB1l/jj/7u1gl9a8KBe4leUPthtP9Tx3k8VommJ/80y8JAX2tBoQtvwRCuAsI3" +
            "GWBvAGtnuf7dRkjJBEGBeHSZrgpX+FM+LxTsJBYJ27Dta4isNtXvwQAmEYirI9dcV29WnwhuqrAbJ9orKcHp8muY+U0NDA=="
    }
}
