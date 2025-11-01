package fit

import java.awt.image.BufferedImage
import Transformations._

object ImagePipeline {

  /** Обработка изображения с выбором эффекта (чисто) */
  def pipeline(
        img: BufferedImage,
        effect: String = "cinematic",
        glitchShift: Int = 10,
        glitchIntensity: Int = 8,
        glitchData: Option[Transformations.GlitchData] = None,
    ): BufferedImage = {

      effect match {
        case "cinematic" => smartFilter(img)
        case "retro"     => retroFilmPure(img, intensity = 2, vignette = 0.5)
        case "noir"      => noirPure(img, intensity = 1.0, grain = 0.4)
        case "glitch"    =>
          glitchData match {
            case Some(data) => glitchChannelsPure(img, data, glitchIntensity, glitchShift)
            case None       => throw new IllegalArgumentException("GlitchData must be provided for glitch effect")
          }
        case _ => smartFilter(img)
      }
  }
}
