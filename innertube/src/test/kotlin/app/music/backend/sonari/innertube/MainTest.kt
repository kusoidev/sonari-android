package app.music.backend.sonari.innertube

import app.music.backend.sonari.innertube.YouTube.SearchFilter
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
  val result = YouTube.search("fakira", SearchFilter("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D"))
  println("Result: $result")
  if (result.isSuccess) {
    val items = result.getOrNull()?.items
    println("Items size: ${items?.size}")
    items?.forEach { println("Item: $it") }
  } else {
    println("Error: ${result.exceptionOrNull()}")
  }
}
