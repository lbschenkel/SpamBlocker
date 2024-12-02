package spam.blocker.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull


// Run multiple blocking functions simultaneously, it returns immediately
//  when any competitor return a non-null value, other threads will be canceled.
// return:
//   Pair(
//     the winner, one of the competitors
//     the first non-null value
//   )
@Suppress("UNCHECKED_CAST")
private suspend fun <C, R> racing(
    competitors: List<C>,
    // runner takes a competitor as param and generate a executable function to run
    runner: (C) -> (()->R),
    timeoutMillis: Long,
): Pair<C?, R?> = coroutineScope {
    if (competitors.isEmpty()) {
        return@coroutineScope Pair(null, null)
    }

    val scope = CoroutineScope(Dispatchers.IO)

    val resultChannel = Channel<Pair<C?,R?>>()

    val jobs = competitors.map { competitor ->
        scope.launch {
            val result = runner(competitor)()

            // set the channel if it's not null
            if (result != null)
                resultChannel.send(
                    Pair(competitor, result as R?)
                )
        }
    }

    val firstNonNullResult = scope.async {
        withTimeoutOrNull(timeoutMillis) {
            resultChannel.receiveCatching()
        }
    }.await()

    // once there is a result, stop all other jobs
    jobs.forEach { job ->
        job.cancel()
    }

    return@coroutineScope Pair(
        firstNonNullResult?.getOrNull()?.first, // the competitor
        firstNonNullResult?.getOrNull()?.second, // the result
    )
}

fun <C, R> race(
    competitors: List<C>,
    // runner takes a competitor as param and generate a executable function to run
    runner: (C) -> (()->R),
    timeoutMillis: Long,
):  Pair<C?, R?>  {
    return runBlocking {
        racing(competitors, runner, timeoutMillis)
    }
}
