package breeze

/*
 Copyright 2012 David Hall

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/

import generic.{URFunc, UReduceable, UFunc}
import linalg.operators.{OpSub, BinaryOp}
import linalg.{NumericOps, QuasiTensor, Tensor}
import scala.math._
import scala.{math=>m}
import org.apache.commons.math3.special.{Gamma => G, Erf}

/**
 * Provides some functions left out of java.lang.math.
 *
 * @author dlwh, afwlehmann
 */
package object numerics extends UniversalFuncs {

  val inf, Inf = Double.PositiveInfinity
  val nan, NaN = Double.NaN

  /**
   * Evaluates the log of the generalized beta function.
   *  \sum_a lgamma(c(a))- lgamma(c.sum)
   */
  val lbeta:URFunc[Double, Double] = new URFunc[Double, Double] {
    def apply(cc: TraversableOnce[Double]): Double = {
      var sum = 0.0
      var lgSum = 0.0
      for(v <- cc) {
        sum += v
        lgSum += lgamma(v)
      }
      lgSum - lgamma(sum)
    }

    override def apply(arr: Array[Double], offset: Int, stride: Int, length: Int, isUsed: (Int) => Boolean): Double = {
      var off = offset
      var sum = 0.0
      var lgSum = 0.0
      var i = 0
      while(i < length) {

        if(isUsed(off))   {
          sum += arr(off)
          lgSum += lgamma(arr(off))
        }

        i += 1
        off += stride
      }
      lgSum - lgamma(sum)
    }
  }

  /**
  * Computes the log of the gamma function.
  *
  * @return an approximation of the log of the Gamma function of x.
  */
  val lgamma:UFunc[Double, Double] = UFunc(G.logGamma _)

  /**
   * The derivative of the log gamma function
   */
  val digamma = UFunc(G.digamma _)

  /**
   * The second derivative of the log gamma function
   */
  val trigamma = UFunc(G.trigamma _)

  /**
   * An approximation to the error function
   */
  val erf = UFunc{ Erf.erf _ }

  /**
   * An approximation to the complementary error function: erfc(x) = 1 - erfc(x)
   */
  val erfc = UFunc{ Erf.erfc _ }

  /**
   * The imaginary error function for real argument x.
   *
   * Adapted from http://www.mathworks.com/matlabcentral/newsreader/view_thread/24120
   * verified against mathematica
   *
   * @return
   */
  val erfi:UFunc[Double, Double] = new UFunc[Double, Double]{
    def apply(x:Double):Double = {
      if(x < 0) -apply(-x)
      else { // taylor expansion
      var y = x
        val x2 = x * x
        var xx = x
        var f = 1.0
        var n  = 0
        while (n < 100) {
          n += 1
          f /= n
          xx *= x2
          val del =  f * xx/(2*n+1)
          if(del < 1E-8) n = 101
          y += del
        }
        y = y*2/m.sqrt(Pi)
        y
      }
    }
  }

  /**
   * Inverse erf
   */
  val erfinv = UFunc{ Erf.erfInv _ }

  /**
   * Inverse erfc
   */
  val erfcinv = UFunc{ Erf.erfcInv _ }

  /**
   * regularized incomplete gamma function  \int_0x \exp(-t)pow(t,a-1) dt / Gamma(a)
   * @param a
   * @param x
   * @see http://commons.apache.org/proper/commons-math/apidocs/org/apache/commons/math3/special/Gamma.html#regularizedGammaP(double, double)
   */
  def gammp(a: Double, x: Double) = G.regularizedGammaP(a, x)





  /**
  * log Incomplete gamma function = \log \int_0x \exp(-t)pow(t,a-1) dt
   *
   * Based on NR
  */
  def lgamma(a: Double, x: Double) = _lgamma(a, x)

  /**
  * log Incomplete gamma function = \log \int_0x \exp(-t)pow(t,a-1) dt
  * May store lgamma(a) in lgam(0) if it's non-null and needs to be computed.
   * Based on NR
  */
  private def _lgamma(a: Double, x:Double, lgam: Array[Double] = null):Double = {
    if (x < 0.0 || a <= 0.0) throw new IllegalArgumentException()
    else if(x == 0) 0.0
    else if (x < a + 1.0) {
      var ap = a
      var del, sum = 1.0/a
      var n = 0
      var result = Double.NaN
      while(n < 100) {
        ap += 1
        del *= x/ap
        sum += del
        if (scala.math.abs(del) < scala.math.abs(sum)*1E-7) {
          result = -x+a*m.log(x) + m.log(sum)
          n = 100
        }
        n += 1
      }
      if(lgam != null) lgam(0) = Double.NaN
      if(result.isNaN) throw new ArithmeticException("Convergence failed")
      else result
    } else {
      val gln = lgamma(a)
      var b = x+1.0-a
      var c = 1.0/1.0e-30
      var d = 1.0/b
      var h = d
      var n = 0
      while(n < 100) {
        n += 1
        val an = -n*(n-a)
        b += 2.0
        d = an*d+b
        if (scala.math.abs(d) < 1E-30) d = 1E-30
        c = b+an/c
        if (scala.math.abs(c) < 1E-30) c = 1E-30
        d = 1.0/d
        val del = d*c
        h *= del
        if (scala.math.abs(del-1.0) < 1E-7) n = 101
      }
      if(lgam != null) lgam(0) = gln
      if (n == 100) throw new ArithmeticException("Convergence failed")
      else logDiff(gln, -x+a*log(x) + m.log(h))
    }
  }

  /**
  * Sums together things in log space.
  * @return log(exp(a) + exp(b))
  */
  def logSum(a: Double, b: Double) = {
    if (a.isNegInfinity) b
    else if (b.isNegInfinity) a
    else if (a < b) b + scala.math.log1p(exp(a - b))
    else a + scala.math.log1p(exp(b - a))
  }

  /**
  * Sums together things in log space.
  * @return log(\sum exp(a_i))
  */
  def logSum(a: Double, b: Double, c: Double*): Double = {
    if (c.length == 0)
      logSum(a, b)
    else
      logSum(logSum(a, b) +: c)
  }

  /**
  * Sums together things in log space.
  * @return log(\sum exp(a_i))
  */
  def logSum(iter: Iterator[Double], max: Double): Double = {
    require(iter.hasNext)
    if (max.isInfinite) {
      max
    } else {
      val aux = (0.0 /: iter) {
        (acc, x) => if (x.isNegInfinity) acc else acc + exp(x-max)
      }
      if (aux != 0)
        max + scala.math.log(aux)
      else
        max
    }
  }

  /**
  * Sums together things in log space.
  * @return log(\sum exp(a_i))
  */
  def logSum(a: Seq[Double]): Double = {
    a.length match {
      case 0 => Double.NegativeInfinity
      case 1 => a(0)
      case 2 => logSum(a(0), a(1))
      case _ => logSum(a.iterator, a reduceLeft (_ max _))
    }
  }

  /**
   * Sums together the first length elements in log space.
   * The length parameter is used to make things faster.
   *
   * This method needs to be fast. Don't scala-ify it.
   * @return log(\sum^length exp(a_i))
   */
  def logSum(a: Array[Double], length: Int):Double = {
    length match {
      case 0 => Double.NegativeInfinity
      case 1 => a(0)
      case 2 => logSum(a(0),a(1))
      case _ =>
        val m = max(a, length)
        if(m.isInfinite) m
        else {
          var i = 0
          var accum = 0.0
          while(i < length) {
            accum += scala.math.exp(a(i) - m)
            i += 1
          }
          m + scala.math.log(accum)
        }
    }
  }

  /** fast versions of max. Useful for the fast logsum. */
  def max(a: Array[Double], length: Int) = {
    var i = 1
    var max =  a(0)
    while(i < length) {
      if(a(i) > max) max = a(i)
      i += 1
    }
    max

  }

  /**
   * The sigmoid function: 1/(1 + exp(-x))
   *
   *
   */
  def sigmoid = UFunc { (x:Double) => 1/(1+scala.math.exp(-x)) }

  /**
   * Takes the difference of two doubles in log space. Requires a &gt b.
   * Note that this only works if a and b are close in value. For a &gt;&gt; b,
   * this will almost certainly do nothing. (exp(30) - exp(1) \approx exp(30))
   *
   * @return log(exp(a) - exp(b))
   */
  def logDiff(a: Double, b: Double): Double = {
    require(a >= b)
    if (a > b) a + log(1.0 - exp(b-a))
    else Double.NegativeInfinity
  }

  /**
   * Computes the polynomial P(x) with coefficients given in the passed in array.
   * coefs(i) is the coef for the x_i term.
   */
  def polyval(coefs: Array[Double], x: Double) = {
    var i = coefs.length-1
    var p = coefs(i)
    while (i>0) {
      i -= 1
      p = p*x + coefs(i)
    }
    p
   }


  /**
   * closeTo for Doubles.
   */
  def closeTo(a: Double, b: Double, relDiff: Double = 1E-4) = {
    a == b || (scala.math.abs(a-b) < scala.math.max(scala.math.max(scala.math.abs(a),scala.math.abs(b)) ,1) * relDiff)
  }


  /**
   * The indicator function. 1.0 iff b, else 0.0
   */
  val I: UFunc[Boolean, Double] = new UFunc[Boolean, Double] {
    def apply(b: Boolean) = if (b) 1.0 else 0.0
  }

  /**
   * The indicator function in log space: 0.0 iff b else Double.NegativeInfinity
   */
  val logI: UFunc[Boolean, Double] = new UFunc[Boolean, Double] {
    def apply(b: Boolean) = if(b) 0.0 else Double.NegativeInfinity
  }
}


trait UniversalFuncs {
  import scala.{math=>m}
  // TODO: these probably need to be manually specced out because boxing hurts so much
  val exp = UFunc(m.exp _)
  val log = UFunc(m.log _)
  val log1p = UFunc(m.log1p _)

  val sqrt = UFunc(m.sqrt _)

  val sin = UFunc(m.sin _)
  val cos = UFunc(m.cos _)
  val tan = UFunc(m.tan _)

  val asin = UFunc(m.asin _)
  val acos = UFunc(m.acos _)
  val atan = UFunc(m.atan _)
  val toDegrees = UFunc(m.toDegrees _)
  val toRadians = UFunc(m.toRadians _)

  val floor = UFunc(m.floor _)
  val ceil = UFunc(m.ceil _)
  val round = UFunc(m.round _)
  val rint = UFunc(m.rint _)
  val signum = UFunc(m.signum(_:Double))
  val abs = UFunc(m.abs(_:Double))


}