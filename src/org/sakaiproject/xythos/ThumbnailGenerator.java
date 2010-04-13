package org.sakaiproject.xythos;

import java.awt.*;
import java.awt.image.*;
import java.io.*;

public class ThumbnailGenerator 
{
  public static int[] getThumbnailDimensions(int imageWidth, int imageHeight, int maxThumbWidth, int maxThumbHeight) {
    double thumbRatio = (double)maxThumbWidth / (double)maxThumbHeight;
    double imageRatio = (double)imageWidth / (double)imageHeight;
    int thumbHeight = maxThumbHeight;
    int thumbWidth = maxThumbWidth;
    if (thumbRatio < imageRatio) 
    {
      thumbHeight = (int)(thumbWidth / imageRatio);
    } 
    else 
    {
      thumbWidth = (int)(thumbHeight * imageRatio);
    }

    if(imageWidth < thumbWidth && imageHeight < thumbHeight)
    {
      thumbWidth = imageWidth;
      thumbHeight = imageHeight;
    }
    else if(imageWidth < thumbWidth)
      thumbWidth = imageWidth;
    else if(imageHeight < thumbHeight)
      thumbHeight = imageHeight;
    return new int[] {thumbWidth, thumbHeight};
  }
  
  public static void transform(InputStream input, final int maxThumbWidth, final int maxThumbHeight, OutputStream out) throws Exception 
  {
    Image image = javax.imageio.ImageIO.read(input);
    int imageWidth    = image.getWidth(null);
    int imageHeight   = image.getHeight(null);
      
      
    int[] d = getThumbnailDimensions(imageWidth, imageHeight, maxThumbWidth, maxThumbHeight);

      BufferedImage thumbImage = new BufferedImage(d[0], d[1], BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics2D = thumbImage.createGraphics();
      graphics2D.setBackground(Color.WHITE);
      graphics2D.setPaint(Color.WHITE); 
      graphics2D.fillRect(0, 0, d[0], d[1]);
      graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      graphics2D.drawImage(image, 0, 0, d[0], d[1], null);
      
      javax.imageio.ImageIO.write(thumbImage, "JPG", out);
  }
}
