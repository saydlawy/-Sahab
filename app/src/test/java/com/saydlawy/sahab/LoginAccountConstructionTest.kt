package com.saydlawy.sahab

import com.saydlawy.sahab.data.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LoginAccountConstructionTest {

    @Test
    fun xtreamAccountUsesNamedParametersForServerUserAndPass() {
        val server = "http://portal.example.com:8080"
        val user = "alice"
        val pass = "secret123"
        val account = Account(
            name = user.ifBlank { "My TV" },
            type = "XTREAM",
            server = server,
            user = user,
            pass = pass,
        )
        assertEquals("XTREAM", account.type)
        assertEquals(server, account.server)
        assertEquals(user, account.user)
        assertEquals(pass, account.pass)
        assertEquals(user, account.name)
        assertNotEquals(server, account.id)
    }
}
