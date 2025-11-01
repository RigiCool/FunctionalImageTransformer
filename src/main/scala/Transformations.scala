package fit

import java.awt.image.BufferedImage
import java.awt.Color
import scala.annotation.tailrec

object Transformations {

  case class GlitchData(rShift: Array[Int], gShift: Array[Int], bShift: Array[Int],
                      rNoise: Array[Array[Int]], gNoise: Array[Array[Int]], bNoise: Array[Array[Int]])

  /** Безопасное ограничение значений */
  private def clamp(v: Double): Int = Math.min(255, Math.max(0, v)).toInt

  /** Универсальный чистый трансформер изображения */
  @tailrec
  private def processPixels(
      img: BufferedImage,
      width: Int,
      height: Int,
      f: Color => Color,
      i: Int = 0,
      acc: List[(Int, Int, Color)] = Nil
  ): List[(Int, Int, Color)] = {
    if (i >= width * height) acc.reverse
    else {
      val x = i % width
      val y = i / width
      val c = new Color(img.getRGB(x, y))
      val newColor = f(c)
      processPixels(img, width, height, f, i + 1, (x, y, newColor) :: acc)
    }
  }

  private def transform(img: BufferedImage)(f: Color => Color): BufferedImage = {
    val width  = img.getWidth
    val height = img.getHeight
    val pixels = processPixels(img, width, height, f)
    val out    = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    pixels.foreach { case (x, y, c) => out.setRGB(x, y, c.getRGB) }
    out
  }

  /** Приведение к более нейтральной серости и стабилизация тонов */
  private def stabilizeGray(c: Color): Color = {
    val avg = (c.getRed + c.getGreen + c.getBlue) / 3.0
    val factor = 0.9 + 0.2 * ((avg - 128) / 128.0)
    val r = clamp(c.getRed * factor)
    val g = clamp(c.getGreen * factor)
    val b = clamp(c.getBlue * factor)
    new Color(r, g, b)
  }

  /** Анализ среднего цвета изображения */
  private def averageColor(img: BufferedImage): (Double, Double, Double) = {
    val width  = img.getWidth
    val height = img.getHeight
    val totals = (0 until width * height).foldLeft((0.0, 0.0, 0.0)) { case ((rSum, gSum, bSum), i) =>
      val c = new Color(img.getRGB(i % width, i / width))
      (rSum + c.getRed, gSum + c.getGreen, bSum + c.getBlue)
    }
    (totals._1 / (width * height), totals._2 / (width * height), totals._3 / (width * height))
  }

  /** Основной умный фильтр с выбором цветовой схемы */
  def smartFilter(img: BufferedImage): BufferedImage = {
    val stabilized = transform(img)(stabilizeGray)
    val (rAvg, gAvg, bAvg) = averageColor(stabilized)

    val filter: Color => Color =
      if (bAvg >= rAvg && bAvg >= gAvg) tealOrangeScheme
      else if (rAvg >= gAvg && rAvg >= bAvg) redGreenScheme
      else violetYellowScheme

    val colored = transform(stabilized)(filter)
    transform(colored)(enhanceContrastAndSaturation)
  }

  private def tealOrangeScheme(c: Color): Color = {
    val brightness = 0.299*c.getRed + 0.587*c.getGreen + 0.114*c.getBlue
    if brightness < 128 then new Color(
      clamp(c.getRed * 0.9),
      clamp(c.getGreen * 0.9),
      clamp(c.getBlue * 1.1 + 10)
    )
    else new Color(
      clamp(c.getRed * 1.1 + 10),
      clamp(c.getGreen * 1.05),
      clamp(c.getBlue * 0.9)
    )
  }

  private def redGreenScheme(c: Color): Color = {
    val brightness = 0.299*c.getRed + 0.587*c.getGreen + 0.114*c.getBlue
    if brightness < 128 then new Color(
      clamp(c.getRed * 1.1 + 10),
      clamp(c.getGreen * 0.9),
      clamp(c.getBlue * 0.8)
    )
    else new Color(
      clamp(c.getRed * 1.2 + 10),
      clamp(c.getGreen * 1.05),
      clamp(c.getBlue * 0.7)
    )
  }

  private def violetYellowScheme(c: Color): Color = {
    val brightness = 0.299 * c.getRed + 0.587 * c.getGreen + 0.114 * c.getBlue

    if (brightness < 128)
      new Color(
        clamp(c.getRed * 1.05 + c.getBlue * 0.1),  // меньше добавки синего к красному
        clamp(c.getGreen * 0.95),                  // чуть меньше гасим зелёный
        clamp(c.getBlue * 1.1)                     // лёгкое увеличение синего
      )
    else
      new Color(
        clamp(c.getRed * 1.08 + 5),                // мягче осветляем
        clamp(c.getGreen * 1.05 + 5),
        clamp(c.getBlue * 0.9)                     // немного убираем синеву
      )
  }


  private def enhanceContrastAndSaturation(c: Color): Color = {
    val contrast = 1.05
    val sat = 1.1

    val r = c.getRed / 255.0
    val g = c.getGreen / 255.0
    val b = c.getBlue / 255.0

    val avg = (r + g + b) / 3.0
    val nr = clamp((avg + (r - avg) * sat) * contrast * 255)
    val ng = clamp((avg + (g - avg) * sat) * contrast * 255)
    val nb = clamp((avg + (b - avg) * sat) * contrast * 255)

    new Color(nr, ng, nb)
  }

  /** Чистый Glitch */
  /** Реалистичный glitch-эффект: смещение строк, RGB рассинхрон и артефакты */
  def glitchChannelsPure(
      img: BufferedImage, 
      data: GlitchData, 
      intensity: Int = 8,   // умножает шум
      maxShift: Int = 10    // ограничивает сдвиг каналов
  ): BufferedImage = {
    val width  = img.getWidth
    val height = img.getHeight

    val out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    (0 until height).foreach { yy =>
      val rShiftRow = clampShift(data.rShift(yy), maxShift)
      val gShiftRow = clampShift(data.gShift(yy), maxShift)
      val bShiftRow = clampShift(data.bShift(yy), maxShift)

      (0 until width).foreach { xx =>
        val sxR = Math.min(width - 1, Math.max(0, xx + rShiftRow))
        val sxG = Math.min(width - 1, Math.max(0, xx + gShiftRow))
        val sxB = Math.min(width - 1, Math.max(0, xx + bShiftRow))

        val r = new Color(img.getRGB(sxR, yy)).getRed
        val g = new Color(img.getRGB(sxG, yy)).getGreen
        val b = new Color(img.getRGB(sxB, yy)).getBlue

        val nr = clamp(r + data.rNoise(yy)(xx) * intensity / 8)
        val ng = clamp(g + data.gNoise(yy)(xx) * intensity / 8)
        val nb = clamp(b + data.bNoise(yy)(xx) * intensity / 8)

        out.setRGB(xx, yy, new Color(nr, ng, nb).getRGB)
      }
    }

    out
  }

  // Вспомогательная функция для ограничения сдвига
  private def clampShift(value: Int, maxShift: Int): Int =
    Math.min(maxShift, Math.max(-maxShift, value))

  /** Чистый Noir */
  def noirPure(img: BufferedImage, intensity: Double = 1.0, grain: Double = 0.4): BufferedImage = {
    val width = img.getWidth
    val height = img.getHeight
    val out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

    (0 until height).foreach { y =>
      (0 until width).foreach { x =>
        val c = new Color(img.getRGB(x, y))
        val gray = 0.299 * c.getRed + 0.587 * c.getGreen + 0.114 * c.getBlue
        val contrasted = 128 + (gray - 128) * (1.5 * intensity)
        val n = 0.1 * 20 * grain
        val finalGray = clamp(contrasted + n)
        out.setRGB(x, y, new Color(finalGray, finalGray, finalGray).getRGB)
      }
    }
    out
  }

  /** Чистый RetroFilm — принимает массив случайных чисел */
  def retroFilmPure(img: BufferedImage, intensity: Double = 2, vignette: Double = 0.4): BufferedImage = {
    val width  = img.getWidth
    val height = img.getHeight
    val out    = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

    val centerX = width / 2.0
    val centerY = height / 2.0
    val maxDist = Math.sqrt(centerX * centerX + centerY * centerY)

    (0 until height).foreach { y =>
      (0 until width).foreach { x =>
        val c = new Color(img.getRGB(x, y))
        val r = c.getRed / 255.0
        val g = c.getGreen / 255.0
        val b = c.getBlue / 255.0

        val avg = (r + g + b) / 3.0
        val fadedR = avg + (r - avg) * 0.85
        val fadedG = avg + (g - avg) * 0.9
        val fadedB = avg + (b - avg) * 0.8

        val warmR = fadedR * (1.1 + intensity * 0.1)
        val warmG = fadedG * (1.05 + intensity * 0.05)
        val warmB = fadedB * (0.95 - intensity * 0.05)

        val dx = x - centerX
        val dy = y - centerY
        val dist = Math.sqrt(dx * dx + dy * dy)
        val vignetteFactor = 1.0 - vignette * Math.pow(dist / maxDist, 2.0)

        val n = 0.1 * 0.03 * intensity
        val nr = clamp((warmR * vignetteFactor + n) * 255)
        val ng = clamp((warmG * vignetteFactor + n) * 255)
        val nb = clamp((warmB * vignetteFactor + n) * 255)

        out.setRGB(x, y, new Color(nr, ng, nb).getRGB)
      }
    }
    out
  }

}
