import java.awt.Paint;
import javax.swing.JPanel;
import java.awt.Graphics;

public class MapPanel extends JPanel {
	private VMS v;
	public MapPanel(VMS v) {
		this.v = v;
	}
	
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		g.drawLine(250, 0, 250, 500);
		g.drawLine(0, 250, 500, 250);
		final Object[][] obj = v.getData();
		for (int i=0; i <v.getCount();i++) {
			int j = (int) Math.ceil((Double)obj[i][2]/20);
			int k = (int) Math.ceil((Double)obj[i][3]/20);
			g.drawOval(j+250, k+250, 1, 1);
			g.drawLine(j+250, k+250, 250, 250);
		}
	}

}