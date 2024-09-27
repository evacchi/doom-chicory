package com.stepstone.jc.demo;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

import static com.stepstone.jc.demo.Doom.doomScreenHeight;
import static com.stepstone.jc.demo.Doom.doomScreenWidth;
import java.awt.image.BufferedImage;


public class GameWindow extends JFrame {
    private final GamePanel innerPanel = new GamePanel();
    private final DoomKeyListener keyListener = new DoomKeyListener();

    public GameWindow() throws HeadlessException {
        super("Doom on Chicory");

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLocationByPlatform(true);
        setContentPane(innerPanel);
        addKeyListener(keyListener);

        innerPanel.setPreferredSize(new Dimension(doomScreenWidth, doomScreenHeight));
        innerPanel.setBackground(Color.BLACK);
        pack();
    }

    void drawImage(BufferedImage image) {
        innerPanel.getGraphics().drawImage(image, 0, 0, innerPanel.getWidth(), innerPanel.getHeight(), null);
        innerPanel.setImage(image);
        innerPanel.repaint(); // Trigger a repaint when the image is updated
    }

    public void drainKeyEvents(Consumer<int[]> action) {
        keyListener.drainEvents(action);
    }
}

class GamePanel extends JPanel {
    private BufferedImage image;
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image != null) {
            g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
        }
    }
    public void setImage(BufferedImage image) {
        this.image = image;
    }
}
