package fit

import cats.effect._
import cats.implicits._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.staticcontent._
import org.http4s.server.Router
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.MediaType

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import javax.imageio.ImageIO
import scala.util.Random

object WebServer extends IOApp {

  // ---------- Form Post ----------
  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] { req =>
    if (req.method == Method.POST && req.uri.path.renderString == "/process") {
      req.decode[org.http4s.multipart.Multipart[IO]] { multipart =>

        val process: IO[Response[IO]] = for {
          // Get file
          filePart <- IO.fromOption(multipart.parts.find(_.name.contains("file")))(
                        new Exception("File not found in form-data")
                      )
          bytes <- filePart.body.compile.toVector.map(_.toArray)
          img <- IO(ImageIO.read(new ByteArrayInputStream(bytes)))
          _ <- IO.raiseWhen(img == null)(new RuntimeException("Не удалось прочитать изображение"))

          // Parameters from the form
          effect <- multipart.parts.find(_.name.contains("effect"))
                      .map(p => p.bodyText.compile.string.map(_.trim))
                      .getOrElse(IO.pure("cinematic"))

          glitchShift <- multipart.parts.find(_.name.contains("glitchShift"))
                          .map(p => p.bodyText.compile.string.map(s => s.trim.toIntOption.getOrElse(10)))
                          .getOrElse(IO.pure(10))

          glitchIntensity <- multipart.parts.find(_.name.contains("glitchIntensity"))
                              .map(p => p.bodyText.compile.string.map(s => s.trim.toIntOption.getOrElse(8)))
                              .getOrElse(IO.pure(8))

          _ <- IO.println(s"Effect: $effect, shift=$glitchShift, intensity=$glitchIntensity")

          // Generate GlitchData
          glitchDataOpt <- if (effect == "glitch") generateGlitchData(img, glitchShift, glitchIntensity)
                           else IO.pure(None)

          // Pipeline
          processed <- IO(ImagePipeline.pipeline(img, effect, glitchData = glitchDataOpt))

          // Convert png
          bytesOut <- IO {
            val baos = new ByteArrayOutputStream()
            ImageIO.write(processed, "png", baos)
            baos.toByteArray
          }

          resp <- Ok(bytesOut).map(_.withContentType(`Content-Type`(MediaType.image.png)))

        } yield resp

        // Error handling
        process.handleErrorWith { e =>
          IO.println(s"Ошибка обработки: ${e.getMessage}") *> 
            BadRequest(s"Ошибка: ${e.getMessage}")
        }
      }
    } else NotFound("Route not found")
  }

  // ---------- GlitchData ----------
  private def generateGlitchData(img: java.awt.image.BufferedImage, maxShift: Int, intensity: Int): IO[Option[Transformations.GlitchData]] =
    IO {
      val rand = new Random()
      val height = img.getHeight
      val width = img.getWidth
      val blockProb = 0.01

      val rShift = Array.fill(height)(0)
      val gShift = Array.fill(height)(0)
      val bShift = Array.fill(height)(0)

      var y = 0
      while (y < height) {
        if (rand.nextDouble() < blockProb) {
          val bh = Math.min(height - y, 3 + rand.nextInt(10))
          val rBlock = rand.nextInt(maxShift * 2 + 1) - maxShift
          val gBlock = rand.nextInt(maxShift * 2 + 1) - maxShift
          val bBlock = rand.nextInt(maxShift * 2 + 1) - maxShift
          for (yy <- y until (y + bh)) {
            rShift(yy) = rBlock
            gShift(yy) = gBlock
            bShift(yy) = bBlock
          }
          y += bh
        } else {
          rShift(y) = rand.nextInt(maxShift / 3 + 1) - maxShift / 6
          gShift(y) = rand.nextInt(maxShift / 3 + 1) - maxShift / 6
          bShift(y) = rand.nextInt(maxShift / 3 + 1) - maxShift / 6
          y += 1
        }
      }

      val rNoise = Array.fill(height, width)(rand.nextInt(2 * intensity + 1) - intensity)
      val gNoise = Array.fill(height, width)(rand.nextInt(2 * intensity + 1) - intensity)
      val bNoise = Array.fill(height, width)(rand.nextInt(2 * intensity + 1) - intensity)

      Some(Transformations.GlitchData(rShift, gShift, bShift, rNoise, gNoise, bNoise))
    }

  // ---------- Routes ----------
  val staticRoutes = fileService[IO](FileService.Config(
    systemPath = "src/main/resources",
    pathPrefix = "/"
  ))

  val httpApp = Router(
    "/" -> staticRoutes,
    "/process" -> routes
  ).orNotFound

  // ---------- Server ----------
  override def run(args: List[String]): IO[ExitCode] =
    for {
      _ <- IO.println("🚀 Server started on http://localhost:8080")
      exit <- BlazeServerBuilder[IO]
                .bindHttp(8080, "0.0.0.0")
                .withHttpApp(httpApp)
                .serve
                .compile
                .drain
                .as(ExitCode.Success)
    } yield exit
}
