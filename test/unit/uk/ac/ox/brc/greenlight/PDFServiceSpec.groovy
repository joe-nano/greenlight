package uk.ac.ox.brc.greenlight

import grails.test.mixin.TestFor
import org.apache.commons.io.FileUtils
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.MultipartFile
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@TestFor(PDFService)
class PDFServiceSpec extends Specification {


	def setup() {
	}

	def cleanup() {
	}

//	void "convertPDFToSingleJPGImage will create single Image from all pages in the input PDF"() {
//
//		when:
//		//create multipart file from the test fixture
//		Path path = Paths.get("test/resources/multiPagePDF.pdf");
//		String name = "multiPagePDF.pdf";
//		String originalFileName = "multiPagePDF.pdf";
//		String contentType = "application/pdf";
//		byte[] content = Files.readAllBytes(path);
//		MultipartFile pdf = new MockMultipartFile(name,originalFileName, contentType, content);
//
//		//create single image from PDF
//		def finalMultipartFile = service.convertPDFToSingleJPGImage(pdf,"myFileName.jpg");
//		//prepare to compare the content of the created image with the expected image
//		File createdImageFile =  File.createTempFile("myTemp","jpg")
//		FileOutputStream fos = new FileOutputStream(createdImageFile);
//		fos.write(finalMultipartFile.getBytes());
//		fos.close();
//		byte[] dataCreated  = Files.readAllBytes(createdImageFile.toPath());
//		byte[] dataExpected = Files.readAllBytes(new File("test/resources/multiPageJPG.jpg").toPath());
//
//		then:
//		dataCreated.size() == dataExpected.size()
//		dataCreated.eachWithIndex { byte entry, int i ->
//			assert dataCreated[i] == dataExpected[i]
//		}
//
//		cleanup:
//		createdImageFile.delete()
//	}

	void "convertPDFToSinglePNGImage will create single Image from all pages in the input PDF"() {

		when:
		//create multipart file from the test fixture
		Path path = Paths.get("test/resources/multiPagePDF.pdf");
		String name = "multiPagePDF.pdf";
		String originalFileName = "multiPagePDF.pdf";
		String contentType = "application/pdf";
		byte[] content = Files.readAllBytes(path);
		MultipartFile pdf = new MockMultipartFile(name,originalFileName, contentType, content);

		//create single image from PDF
		def finalMultipartFile = service.convertPDFToSinglePNGImage(pdf,"myFileName.png");
		//prepare to compare the content of the created image with the expected image
		File createdImageFile =  File.createTempFile("myTemp","png")
		FileOutputStream fos = new FileOutputStream(createdImageFile);
		fos.write(finalMultipartFile.getBytes());
		fos.close();
		byte[] dataCreated  = Files.readAllBytes(createdImageFile.toPath());
		byte[] dataExpected = Files.readAllBytes(new File("test/resources/multiPagePNG.png").toPath());

		then:
		dataCreated.size() == dataExpected.size()
		dataCreated.eachWithIndex { byte entry, int i ->
			assert dataCreated[i] == dataExpected[i]
		}

		cleanup:
		createdImageFile.delete()
	}
}