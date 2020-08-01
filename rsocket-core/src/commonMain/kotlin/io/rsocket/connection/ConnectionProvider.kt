package io.rsocket.connection

/*fun*/ interface ConnectionProvider {
    suspend fun connect(): Connection
}

inline fun ConnectionProvider(crossinline connect: suspend () -> Connection): ConnectionProvider = object :
    ConnectionProvider {
    override suspend fun connect(): Connection = connect()
}

fun ConnectionProvider(connection: Connection): ConnectionProvider = ConnectionProvider { connection }
