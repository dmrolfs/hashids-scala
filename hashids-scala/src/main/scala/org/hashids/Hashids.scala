package org.hashids

import scala.annotation.tailrec
import org.hashids.impl._

class Hashids(
    salt: String = "",
    minHashLength: Int = 0,
    alphabet: String = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890") {
  private val (seps, guards, effectiveAlphabet) = {
    val distinctAlphabet = alphabet.distinct

    require(distinctAlphabet.length >= 16, "alphabet must contain at least 16 unique characters")
    require(distinctAlphabet.indexOf(" ") < 0, "alphabet cannot contains spaces")

    val sepDiv = 3.5
    val guardDiv = 12
    val filteredSeps = "cfhistuCFHISTU".filter(x => distinctAlphabet.contains(x))
    val filteredAlphabet = distinctAlphabet.filterNot(x => filteredSeps.contains(x))
    val shuffledSeps = consistentShuffle(filteredSeps, salt)

    val (tmpSeps, tmpAlpha) = {
      if (shuffledSeps.isEmpty || ((filteredAlphabet.length / shuffledSeps.length) > sepDiv)) {
        val sepsTmpLen = Math.ceil(filteredAlphabet.length / sepDiv).toInt
        val sepsLen = if (sepsTmpLen == 1) 2 else sepsTmpLen

        if (sepsLen > shuffledSeps.length) {
          val diff = sepsLen - shuffledSeps.length
          val seps = shuffledSeps + filteredAlphabet.substring(0, diff)
          val alpha = filteredAlphabet.substring(diff)
          (seps, alpha)
        } else {
          val seps = shuffledSeps.substring(0, sepsLen)
          val alpha = filteredAlphabet
          (seps, alpha)
        }
      } else (shuffledSeps, filteredAlphabet)
    }

    val guardCount = Math.ceil(tmpAlpha.length.toDouble / guardDiv).toInt
    val shuffledAlpha = consistentShuffle(tmpAlpha, salt)

    if (shuffledAlpha.length < 3) {
      val guards = tmpSeps.substring(0, guardCount)
      val seps = tmpSeps.substring(guardCount)
      (seps, guards, shuffledAlpha)
    } else {
      val guards = shuffledAlpha.substring(0, guardCount)
      val alpha = shuffledAlpha.substring(guardCount)
      (tmpSeps, guards, alpha)
    }
  }

  def encode(numbers: Long*): String = if (numbers.isEmpty) "" else _encode(numbers:_*)

  def encodeHex(in: String): String = {
    require(in.matches("^[0-9a-fA-F]+$"), "Not a HEX string")

    val matcher = "[\\w\\W]{1,12}".r.pattern.matcher(in)

    @tailrec
    def doSplit(result: List[Long]): List[Long] = {
      if (matcher.find())
        doSplit(java.lang.Long.parseLong("1" + matcher.group, 16) :: result)
      else
        result
    }

    _encode(doSplit(Nil):_*)
  }

  private def _encode(numbers: Long*): String = {
    val indexedNumbers = numbers.zipWithIndex
    val numberHash = indexedNumbers
      .foldLeft[Int](0){ case (acc, (x, i)) =>
        acc + (x % (i+100)).toInt
    }
    val lottery = effectiveAlphabet.charAt(numberHash % effectiveAlphabet.length).toString

    val (tmpResult, tmpAlpha) =
      indexedNumbers.foldLeft[(String, String)]((lottery, effectiveAlphabet)) {
        case ((result, alpha), (x, i)) =>
          val buffer = lottery + salt + alpha
          val newAlpha = consistentShuffle(alpha, buffer.substring(0, alpha.length))
          val last = hash(x, newAlpha)
          val newResult = result + last

          if (i + 1 < numbers.size) {
            val num = x % (last.codePointAt(0) + i)
            val sepsIndex = (num % seps.length).toInt
            (newResult + seps.charAt((num % seps.length).toInt), newAlpha)
          } else {
            (newResult, newAlpha)
          }
      }

    val provisionalResult = {
      if (tmpResult.length < minHashLength) {
        val guardIndex = (numberHash + tmpResult.codePointAt(0)) % guards.length
        val guard = guards.charAt(guardIndex)

        val provResult = guard + tmpResult

        if (provResult.length < minHashLength) {
          val guardIndex = (numberHash + provResult.codePointAt(2)) % guards.length
          val guard = guards.charAt(guardIndex)
          provResult + guard
        } else {
          provResult
        }
      } else tmpResult
    }

    val halfLen = tmpAlpha.length / 2

    @tailrec
    def respectMinHashLength(alpha: String, res: String): String = {
      if (res.length >= minHashLength) {
        res
      } else {
        val newAlpha = consistentShuffle(alpha, alpha);
        val tmpRes = newAlpha.substring(halfLen) + res + newAlpha.substring(0, halfLen);
        val excess = tmpRes.length - minHashLength
        val newRes = if(excess > 0) {
          val startPos = excess / 2
          tmpRes.substring(startPos, startPos + minHashLength)
        } else tmpRes
        respectMinHashLength(newAlpha, newRes)
      }
    }

    respectMinHashLength(tmpAlpha, provisionalResult)
  }

  def decode(hash: String): List[Long] = hash match {
    case "" => Nil
    case x =>
      val res = org.hashids.impl.decode(x, effectiveAlphabet, salt, seps, guards)
      if (encode(res:_*) == hash) res else Nil
  }

  def decodeHex(hash: String): String = {
    decode(hash).map { x =>
      x.toHexString.substring(1).toUpperCase
    }.mkString
  }

  def version = "1.0.0"
}

object Hashids {
  def apply(salt: String) =
    new Hashids(salt)

  def apply(salt: String, minHashLength: Int) =
    new Hashids(salt, minHashLength)

  def apply(salt: String, minHashLength: Int, alphabet: String) =
    new Hashids(salt, minHashLength, alphabet)
}
