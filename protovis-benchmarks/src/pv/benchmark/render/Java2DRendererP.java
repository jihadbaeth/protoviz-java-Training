package pv.benchmark.render;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import pv.mark.constants.MarkType;
import pv.mark.constants.TextAlign;
import pv.mark.constants.TextBaseline;
import pv.render.awt.Colors;
import pv.render.awt.Fonts;
import pv.render.awt.java2D.Images;
import pv.render.awt.java2D.Java2DRenderer;
import pv.render.awt.java2D.Shapes;
import pv.render.awt.java2D.StrokeLib;
import pv.scene.DotItem;
import pv.scene.GroupItem;
import pv.scene.ImageItem;
import pv.scene.Item;
import pv.scene.LabelItem;
import pv.scene.PanelItem;
import pv.scene.LinkItem;
import pv.scene.WedgeItem;
import pv.style.Fill;
import pv.style.Font;
import pv.style.Stroke;
import pv.style.Fill.Solid;
import pv.util.Objects;
import pv.util.Rect;
import pv.util.ThreadPool;

public class Java2DRendererP extends Java2DRenderer {

	private static Java2DRendererP s_instance = null;
	public static Java2DRendererP instance() {
		if (s_instance == null) s_instance = new Java2DRendererP();
		return s_instance;
	}
	
	private final Map<String,GroupRenderer> _map
		= new HashMap<String,GroupRenderer>();
	
	private Rectangle2D _rect = new Rectangle2D.Double();
	private Ellipse2D _circ = new Ellipse2D.Double();
	private Line2D _line = new Line2D.Double();
	private GeneralPath _path = new GeneralPath();
	//private RoundRectangle2D _rrect = new RoundRectangle2D.Double();
	
	private BufferedImage[] _imgs = new BufferedImage[] {
		new BufferedImage(512, 384, BufferedImage.TYPE_INT_ARGB),
		new BufferedImage(512, 384, BufferedImage.TYPE_INT_ARGB),
		new BufferedImage(512, 384, BufferedImage.TYPE_INT_ARGB),
		new BufferedImage(512, 384, BufferedImage.TYPE_INT_ARGB)
	};
	
	public Java2DRendererP() {
		_map.put(MarkType.Area,  new AreaRenderer());
		_map.put(MarkType.Bar,   new BarRenderer());
		_map.put(MarkType.Dot,   new DotRenderer());
		_map.put(MarkType.Image, new ImageRenderer());
		_map.put(MarkType.Label, new LabelRenderer());
		_map.put(MarkType.Link , new LinkRenderer());
		_map.put(MarkType.Line,  new PathRenderer());
		_map.put(MarkType.Rule,  new RuleRenderer());
		_map.put(MarkType.Wedge, new WedgeRenderer());
	}
	
	@Override
	public void render(Graphics2D g, PanelItem layer) {
		g.setRenderingHint(
			RenderingHints.KEY_ANTIALIASING,
			RenderingHints.VALUE_ANTIALIAS_ON);
	    g.setRenderingHint(
	        RenderingHints.KEY_RENDERING,
	        RenderingHints.VALUE_RENDER_QUALITY);
	    g.setRenderingHint(
	        RenderingHints.KEY_INTERPOLATION,
	        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
	    
	    int width = 1024, height = 768;
	    int nrows = 2;
	    int ncols = 2;
	    
	    int w = (int)Math.round(width / (double)ncols);
	    int h = (int)Math.round(height / (double)nrows);
	    
	    List<RenderTile> list = new ArrayList<RenderTile>();
	    for (int i=0, k=0; i<nrows; ++i) {
	    	for (int j=0; j<ncols; ++j) {
//	    		Graphics2D g2 = (Graphics2D) g.create();
//	    		g2.clipRect(j*w, i*h, w, h);
	    		
	    		Graphics2D g2 = _imgs[k++].createGraphics();
	    		g2.setColor(Color.white);
	    		g2.fillRect(0, 0, w, h);
	    		g2.translate(-j*w, -i*h);
	    		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    		    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    		    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    		    
    		    Rect bounds = new Rect(j*w, i*h, w, h);
	    		list.add(new RenderTile(g2, layer, bounds));
	    	}
	    }
	    try {
	    	long t0 = System.currentTimeMillis();
	    	ThreadPool.getThreadPool().invokeAll(list);
	    	System.out.println("\tTT:"+(System.currentTimeMillis()-t0)/1000f
	    			+"\t"+ThreadPool.getThreadCount());
	    	for (int i=0, k=0; i<nrows; ++i) {
		    	for (int j=0; j<ncols; ++j) {
		    		g.drawImage(_imgs[k++], j*w, i*h, null);
		    	}
		    }
	    } catch (Exception e) {
	    	e.printStackTrace();
	    }
	}
	
	public class RenderTile implements Callable<Object>
	{
		public Graphics2D g;
		public PanelItem layer;
		public Rect bounds;
		
		public RenderTile(Graphics2D g, PanelItem layer, Rect bounds) {
			this.g = g;
			this.layer = layer;
			this.bounds = bounds;
		}
		
		@Override
		public Object call() throws Exception {
			renderLayer(g, layer, bounds);
			return null;
		}
	}
	
	public void renderLayer(Graphics2D g, PanelItem layer, Rect bounds) {
		//if (!layer.bounds.intersects(bounds)) return;
		
		// translate origin as needed
		boolean translate = layer.left != 0 || layer.top != 0;
		if (translate) {
			g.translate(layer.left, layer.top);
			bounds.x -= layer.left;
			bounds.y -= layer.top;
		}
		
		// draw panel background / border
		if (layer.fill != null || layer.stroke != null) {
			_rect.setRect(layer.left, layer.top, layer.width, layer.height);
			if (layer.fill != null) {
				fill(layer.fill, layer.alpha, g);
				g.fill(_rect);
			}
			if (layer.stroke != null) {
				stroke(layer.stroke, layer.alpha, g);
				g.draw(_rect);
			}
		}
		
		// recursively render groups
		List<GroupItem> items = preprocess(layer.items);
		GroupRenderer gr = null; String type = null;
		for (int i=0; i<items.size(); ++i)
		{
			GroupItem group = items.get(i);
			if (group == null) continue;
			// lookup renderer if we switch types
			if (type != group.type) {
				type = group.type;
				gr = _map.get(type);
			}	
			if (group instanceof PanelItem) {
				renderLayer(g, (PanelItem)group, bounds);
			} else if (group.type == MarkType.Panel) {
				List<GroupItem> layers = preprocess(group.items);
				for (Item item : layers) {
					renderLayer(g, (PanelItem)item, bounds);
				}
				Objects.List.reclaim(layers);
				group.computeBounds();
			} else if (group.visible) {
				//if (group.bounds.intersects(bounds)) {
					gr.render(group, g, bounds);
					group.computeBounds();
				//}
			}
		}
		Objects.List.reclaim(items);
		
		// translate origin back
		if (translate) {
			g.translate(-layer.left, -layer.top);
			bounds.x += layer.left;
			bounds.y += layer.top;
		}
		layer.computeBounds();
	}
	
	private void fill(Fill fill, double alpha, Graphics2D g) {
		Solid sf = (Solid)fill;
		g.setColor(Colors.getColor(sf.color(), alpha*sf.alpha()));
	}
	
	private void stroke(Stroke s, double alpha, Graphics2D g) {
		fill(s.fill(), alpha, g);
		g.setStroke(StrokeLib.getStroke((float)s.width()));
	}
	
	private void font(Font f, Graphics2D g) {
		int style = java.awt.Font.PLAIN;
		if (f.bold()) style |= java.awt.Font.BOLD;
		if (f.italic()) style |= java.awt.Font.ITALIC;
		g.setFont(Fonts.getFont(f.name(), style, f.size()));
	}
	
	// -- Mark Renderers ------------------------------------------------------
	
	abstract class GroupRenderer {
		public abstract void render(Item group, Graphics2D g, Rect bounds);
	}
	class AreaRenderer extends GroupRenderer {
		public void render(Item group, Graphics2D g, Rect bounds) {
			List<Item> items = group.items();
			if (items.size() == 0) return;
			
			Item first = items.get(0);
			if (!first.visible) return;
			boolean fb = first.fill != null;
			boolean sb = first.stroke != null;
			if (!(fb || sb)) return;
			
			_path.reset();
			_path.moveTo(first.left, first.top);
			for (int i=1; i<items.size(); ++i) {
				Item item = items.get(i);
				_path.lineTo(item.left, item.top);
			}
			for (int i=items.size(); --i>=0;) {
				Item item = items.get(i);
				_path.lineTo(item.left + item.width, item.top + item.height);
			}
			_path.closePath();
			
			if (fb) {
				fill(first.fill, first.alpha, g);
				g.fill(_path);
			}
			if (sb) {
				stroke(first.stroke, first.alpha, g);
				g.draw(_path);
			}
		}
	}
	class BarRenderer extends GroupRenderer {
		public void render(Item group, Graphics2D g, Rect bounds) {
			List<Item> items = group.items();
			boolean fb = false, sb = false;
			
			for (int i=0; i<items.size(); ++i) {
				Item item = items.get(i);
				if (!item.visible) continue;
				fb = item.fill != null;
				sb = item.stroke != null;
				if (!bounds.intersects(item.left, item.top, item.width, item.height))
					continue;

				if (fb || sb) {
					_rect.setRect(item.left, item.top, item.width, item.height);
					if (fb) {
						fill(item.fill, item.alpha, g);
						g.fill(_rect);
					}
					if (sb) {
						stroke(item.stroke, item.alpha, g);
						g.draw(_rect);
					}
				}
			}
		}
	}
	// TODO: currently draws circles only. Fix this.
	class DotRenderer extends GroupRenderer {	
		public void render(Item group, Graphics2D g, Rect bounds) {
			List<Item> items = group.items();
			boolean fb = false, sb = false;
			String shape = null;
			int dotCount = 0;
			
			for (int i=0; i<items.size(); ++i) {
				DotItem item = (DotItem) items.get(i);
				if (!item.visible) continue;
				fb = item.fill != null;
				sb = item.stroke != null;
				if (!(fb || sb) || item.shape==null) continue;
				if (item.shape != shape) {
					shape = item.shape;
				}
				
				_circ.setFrameFromCenter(item.left, item.top,
					item.left+item.radius, item.top+item.radius);
				if (!bounds.intersects(_circ.getMinX(), _circ.getMinY(), _circ.getWidth(), _circ.getHeight()))
					continue;
				++dotCount;
				Shape ss = _circ;
				
				if (fb) {
					fill(item.fill, item.alpha, g);
					g.fill(ss);
				}
				if (sb) {
					stroke(item.stroke, item.alpha, g);
					g.draw(ss);
				}
			}
			System.out.println("\tDC:"+dotCount);
		}
	}
	class ImageRenderer extends GroupRenderer {
		private Images images = new Images();
		private AffineTransform transform = new AffineTransform();
		
		public void render(Item group, Graphics2D g, Rect bounds) {
			List<Item> items = group.items();
			
			for (int i=0; i<items.size(); ++i) {
				ImageItem item = (ImageItem) items.get(i);
				if (!item.visible || item.url == null) continue;
				BufferedImage img = images.getImage(item.url);
				if (img == null) continue;
				
				double w = img.getWidth() / 2;
				double h = img.getHeight() / 2;
				double s = 1; // TODO image scaling as needed
				transform.setTransform(s, 0, 0, s, item.left-w, item.top-h);
				g.drawImage(img, transform, null);
			}
		}
	}
	class LabelRenderer extends GroupRenderer {
		public void render(Item group, Graphics2D g, Rect bounds) {
			List<Item> items = group.items();
			Font font = null;
			FontMetrics fm = null;
			
			for (int i=0; i<items.size(); ++i) {
				LabelItem item = (LabelItem) items.get(i);
				if (!item.visible || item.fill == null || item.font == null ||
					item.text == null || item.text.length()==0)
					continue;
				
				fill(item.fill, item.alpha, g);
				if (item.font != font) {
					font = item.font;
					font(font, g);
					fm = g.getFontMetrics();
				}
				
				double x = 0, y = 0;
				
				/* Vertical alignment. */
				double lh = fm.getAscent();
				if (item.textBaseline == TextBaseline.Middle) {
					y += lh / 2;
				} else if (item.textBaseline == TextBaseline.Bottom) {
					y -= item.textMargin;
				} else {
					y += item.textMargin + lh;
				}
				
				if (item.textAlign == TextAlign.Center) {
					x -= fm.stringWidth(item.text) / 2;
				} else if (item.textAlign == TextAlign.Right) {
					x -= fm.stringWidth(item.text) + item.textMargin;
				} else {
					x += item.textMargin;
				}
				
				x += item.left;
				y += item.top; // TODO multiple lines

				if (item.textAngle == 0) {
					g.drawString(item.text, (float) x, (float) y);
				} else {
					// TODO test/refine text rotation
					g.rotate(item.textAngle);
					g.drawString(item.text, (float)x, (float)y);
					g.rotate(-item.textAngle);
				}
			}
		}
	}
	class LinkRenderer extends GroupRenderer {
		public void render(Item group, Graphics2D g, Rect bounds) {
			List<Item> items = group.items();
			for (int i=0; i<items.size(); ++i) {
				LinkItem edge = (LinkItem)items.get(i);
				if (!edge.visible || edge.stroke==null) continue;
				
				stroke(edge.stroke, edge.alpha, g);
				_line.setLine(edge.source.left, edge.source.top,
							  edge.target.left, edge.target.top);
				g.draw(_line);
			}
		}
	}
	class PathRenderer extends GroupRenderer {
		public void render(Item group, Graphics2D g, Rect bounds) {
			List<Item> items = group.items();
			if (items.size() == 0) return;
			
			Item first = items.get(0);
			boolean fb = first.fill != null;
			boolean sb = first.stroke != null;
			if (!(fb || sb)) return;
			
			_path.reset();
			_path.moveTo(first.left, first.top);
			for (int i=1; i<items.size(); ++i) {
				Item item = items.get(i);
				if (item.visible) {
					_path.lineTo(item.left, item.top);
				} else {
					_path.moveTo(item.left, item.top);
				}
			}
			// if closed path
			//_path.closePath();
			
			if (fb) {
				fill(first.fill, first.alpha, g);
				g.fill(_path);
			}
			if (sb) {
				stroke(first.stroke, first.alpha, g);
				g.draw(_path);
			}
		}
	}
	class RuleRenderer extends GroupRenderer {
		public void render(Item group, Graphics2D g, Rect bounds) {
		}
	}
	class WedgeRenderer extends GroupRenderer {
		public void render(Item group, Graphics2D g, Rect bounds) {
			List<Item> items = group.items();
			boolean fb = false, sb = false;
			
			for (int i=0; i<items.size(); ++i) {
				WedgeItem item = (WedgeItem) items.get(i);
				fb = item.fill != null;
				sb = item.stroke != null;

				if (fb || sb) {
					_path.reset();
					Shapes.drawWedge(_path, item);
					if (fb) {
						fill(item.fill, item.alpha, g);
						g.fill(_path);
					}
					if (sb) {
						stroke(item.stroke, item.alpha, g);
						g.draw(_path);
					}
				}
			}
		}
	}
	
}
