package uk.ac.imperial.cisbio.adam.handlers;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.activation.MimeType;
import javax.imageio.ImageIO;

import loci.formats.FormatException;
import loci.formats.gui.BufferedImageReader;

import org.apache.tika.metadata.Metadata;

import uk.ac.imperial.cisbio.adam.ADAM;
import uk.ac.imperial.cisbio.adam.Handler;
import uk.ac.imperial.cisbio.adam.Item;
import uk.ac.imperial.cisbio.adam.ItemDAO;
import uk.ac.imperial.cisbio.adam.Util;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Reader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

public class ImageHandler extends Handler {
	public ImageHandler() {
		super("image", "image/*");
	}

	@Override
	public boolean handle(Item item, Metadata metadata, ItemDAO items) throws Exception {
		String path = item.getFile().getPath();
		item.image_count = Long.parseLong(metadata.get("Image-Count"));
		item.thumbnail = ADAM.fu.store(thumbnail(path), "image/png");
		if (Arrays.asList("gif", "png", "jpeg").contains(new MimeType(item.format).getSubType())) {
			item.barcode = barcode(path);
			//			if (item.barcode != null) {
			//				//TODO this isn't ideal, but currently the only way to get the username
			//				item.organism = OmixedHelper.getBioSourceOrganism(item.path.split(File.separator)[1], item.barcode);
			//			}
		}
		return true;
	}

	static byte[] thumbnail(String path) throws IOException, FormatException {
		return Util.toBytes(thumbnail(new File(path)), "png");
	}

	private static BufferedImage thumbnail(File file) throws IOException, FormatException {
		BufferedImageReader reader = new BufferedImageReader();
		reader.setId(file.getPath());
		BufferedImage thumbnail = reader.openThumbImage(0);
		reader.close();
		return thumbnail;
	}

	//TODO check Readers are thread-safe
	private static Reader reader = new MultiFormatReader();
	private static String barcode(String path) throws ChecksumException, com.google.zxing.FormatException, IOException {
		Map<DecodeHintType, Object> hints = new HashMap<DecodeHintType, Object>();
		hints.put(DecodeHintType.TRY_HARDER, true);
		try {
			return reader.decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(ImageIO.read(new File(path))))), hints).getText();
		} catch (NotFoundException e) {
			return null;
		} catch (ArrayIndexOutOfBoundsException e) {//TODO resources/html/img/buttons-disabled.png
			return null;
		}
	}
}
