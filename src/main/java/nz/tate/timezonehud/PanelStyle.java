/*
 * Copyright (c) 2026, dan <dan@tate.nz>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package nz.tate.timezonehud;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The side panel's look, shared by every section: rounded cards with a heading, small
 * orange text links for actions, and stacks that leave no gap for hidden rows.
 */
final class PanelStyle
{
	static final Color ACCENT = ColorScheme.BRAND_ORANGE;
	static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
	static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	static final Color CARD_EDGE = new Color(52, 52, 52);
	static final Color ROW_HOVER = ColorScheme.DARKER_GRAY_HOVER_COLOR;
	static final int GAP = 8;

	private PanelStyle()
	{
	}

	/** A section: a rounded card with a heading (and something to its right), then the body. */
	static JPanel card(String heading, JComponent accessory, JComponent body)
	{
		JPanel card = new Rounded(new BorderLayout(0, GAP), CARD, CARD_EDGE);
		card.setBorder(new EmptyBorder(GAP + 1, GAP + 1, GAP + 1, GAP + 1));
		JPanel top = new JPanel(new BorderLayout(GAP, 0));
		top.setOpaque(false);
		top.add(heading(heading), BorderLayout.WEST);
		if (accessory != null)
		{
			top.add(accessory, BorderLayout.EAST);
		}
		card.add(top, BorderLayout.NORTH);
		card.add(body, BorderLayout.CENTER);
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		return card;
	}

	static JLabel heading(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(Color.WHITE);
		return label;
	}

	static JLabel small(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(MUTED);
		label.setMinimumSize(new Dimension(0, 0));
		return label;
	}

	static JLabel text(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(Color.WHITE);
		label.setMinimumSize(new Dimension(0, 0));
		return label;
	}

	/** A small orange text action, brighter on hover. Disabled links go grey and do nothing. */
	static JLabel link(String text, String tooltip, Runnable action)
	{
		JLabel link = new JLabel(text);
		link.setFont(FontManager.getRunescapeSmallFont());
		link.setForeground(ACCENT);
		link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		link.setToolTipText(tooltip);
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (link.isEnabled())
				{
					link.setForeground(ACCENT.brighter());
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				link.setForeground(link.isEnabled() ? ACCENT : ColorScheme.MEDIUM_GRAY_COLOR);
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (link.isEnabled())
				{
					action.run();
				}
			}
		});
		return link;
	}

	static void enable(JLabel link, boolean enabled)
	{
		link.setEnabled(enabled);
		link.setForeground(enabled ? ACCENT : ColorScheme.MEDIUM_GRAY_COLOR);
		link.setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
	}

	/** A top-to-bottom stack, each child as wide as the stack, with {@code gap} between visible ones. */
	static JPanel column(int gap)
	{
		JPanel column = new JPanel()
		{
			@Override
			public Component add(Component c)
			{
				if (gap > 0 && getComponentCount() > 0)
				{
					super.add(new Gap(gap, c));
				}
				if (c instanceof JComponent)
				{
					((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
				}
				return super.add(c);
			}
		};
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setOpaque(false);
		return column;
	}

	/** Caps a component's height at what it asks for, so a stack doesn't stretch it. */
	static <T extends JComponent> T fixedHeight(T component)
	{
		component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
		return component;
	}

	/** A gap in a stack that disappears along with the component after it. */
	private static final class Gap extends JComponent
	{
		private final int size;
		private final Component next;

		Gap(int size, Component next)
		{
			this.size = size;
			this.next = next;
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(0, next.isVisible() ? size : 0);
		}

		@Override
		public Dimension getMinimumSize()
		{
			return getPreferredSize();
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	}

	/** A panel with softly rounded corners and a 1px edge. */
	static class Rounded extends JPanel
	{
		private final Color fill;
		private final Color edge;

		Rounded(LayoutManager layout, Color fill, Color edge)
		{
			super(layout);
			this.fill = fill;
			this.edge = edge;
			setOpaque(false);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(fill);
			g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
			if (edge != null)
			{
				g2.setColor(edge);
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 4, 4);
			}
			g2.dispose();
		}
	}

	/** A clickable list row that lights up on hover. */
	static JPanel hoverRow(LayoutManager layout, Runnable onClick)
	{
		JPanel row = new JPanel(layout);
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(5, 7, 5, 7));
		if (onClick != null)
		{
			row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			row.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					row.setBackground(ROW_HOVER);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					row.setBackground(ColorScheme.DARK_GRAY_COLOR);
				}

				@Override
				public void mouseClicked(MouseEvent e)
				{
					onClick.run();
				}
			});
		}
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	/** A setting row: its name and a hint on the left, a {@link Switch} on the right. Click anywhere to flip it. */
	static JComponent toggle(Switch control, String name, String hint)
	{
		JPanel words = column(1);
		words.add(text(name));
		if (hint != null)
		{
			words.add(small(hint));
		}
		JPanel row = new JPanel(new BorderLayout(GAP, 0));
		row.setOpaque(false);
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.add(words, BorderLayout.CENTER);
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.add(control, BorderLayout.NORTH);
		row.add(holder, BorderLayout.EAST);
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				control.flip();
			}
		});
		return fixedHeight(row);
	}

	/** A setting row with a control on the right: a stepper or a colour swatch. */
	static JComponent setting(String name, String hint, JComponent control)
	{
		JPanel words = column(1);
		words.add(text(name));
		if (hint != null)
		{
			words.add(small(hint));
		}
		JPanel row = new JPanel(new BorderLayout(GAP, 0));
		row.setOpaque(false);
		row.add(words, BorderLayout.CENTER);
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.add(control, BorderLayout.NORTH);
		row.add(holder, BorderLayout.EAST);
		return fixedHeight(row);
	}

	/** An on/off switch drawn here, so it looks the same whatever the look and feel. */
	static final class Switch extends JComponent
	{
		private static final Color OFF = new Color(72, 72, 72);
		private boolean on;
		private Consumer<Boolean> onChange = v -> {};

		Switch(boolean on)
		{
			this.on = on;
			setPreferredSize(new Dimension(30, 16));
			setMinimumSize(getPreferredSize());
			setMaximumSize(getPreferredSize());
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		}

		void onChange(Consumer<Boolean> onChange)
		{
			this.onChange = onChange;
		}

		boolean isOn()
		{
			return on;
		}

		/** Sets the switch without telling the listener, for keeping it in step with the config. */
		void setOn(boolean on)
		{
			this.on = on;
			repaint();
		}

		void flip()
		{
			on = !on;
			repaint();
			onChange.accept(on);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getPreferredSize().width;
			int h = getPreferredSize().height;
			g2.setColor(on ? ACCENT : OFF);
			g2.fillRoundRect(0, 0, w, h, h, h);
			int knob = h - 4;
			g2.setColor(on ? Color.WHITE : new Color(200, 200, 200));
			g2.fillOval(on ? w - knob - 2 : 2, 2, knob, knob);
			g2.dispose();
		}
	}

	/** A small tick box drawn here: orange with a white tick when ticked. */
	static final class TickBox extends JComponent
	{
		private boolean ticked;
		private Consumer<Boolean> onChange = v -> {};

		TickBox()
		{
			setPreferredSize(new Dimension(14, 14));
			setMinimumSize(getPreferredSize());
			setMaximumSize(getPreferredSize());
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (isEnabled())
					{
						ticked = !ticked;
						repaint();
						onChange.accept(ticked);
					}
				}
			});
		}

		void onChange(Consumer<Boolean> onChange)
		{
			this.onChange = onChange;
		}

		void setTicked(boolean ticked)
		{
			this.ticked = ticked;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (ticked)
			{
				g2.setColor(ACCENT);
				g2.fillRoundRect(0, 0, 14, 14, 4, 4);
				g2.setColor(Color.WHITE);
				g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.drawPolyline(new int[]{3, 6, 11}, new int[]{7, 10, 4}, 3);
			}
			else
			{
				g2.setColor(ColorScheme.DARK_GRAY_COLOR);
				g2.fillRoundRect(0, 0, 14, 14, 4, 4);
				g2.setColor(isEnabled() ? new Color(110, 110, 110) : new Color(70, 70, 70));
				g2.drawRoundRect(0, 0, 13, 13, 4, 4);
			}
			g2.dispose();
		}
	}

	/** A button drawn to match the cards: dark and rounded, lighter on hover. */
	static JComponent button(String text, String tooltip, Runnable action)
	{
		Color fill = new Color(46, 46, 46);
		Color hover = new Color(58, 58, 58);
		JPanel button = new Rounded(new BorderLayout(), fill, new Color(70, 70, 70))
		{
			private boolean over;

			{
				addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseEntered(MouseEvent e)
					{
						over = true;
						repaint();
					}

					@Override
					public void mouseExited(MouseEvent e)
					{
						over = false;
						repaint();
					}

					@Override
					public void mouseClicked(MouseEvent e)
					{
						action.run();
					}
				});
			}

			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(over ? hover : fill);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
				g2.setColor(over ? ACCENT : new Color(70, 70, 70));
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 4, 4);
				g2.dispose();
			}
		};
		JLabel label = new JLabel(text, SwingConstants.CENTER);
		label.setForeground(Color.WHITE);
		button.add(label, BorderLayout.CENTER);
		button.setBorder(new EmptyBorder(6, 8, 6, 8));
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setToolTipText(tooltip);
		return fixedHeight(button);
	}

	/** A whole number with minus and plus either side; the number can also be typed. */
	static final class Stepper extends JPanel
	{
		private final int min;
		private final int max;
		private final JTextField field = new JTextField(3);
		private int value;
		private IntConsumer onChange = v -> {};

		Stepper(int value, int min, int max)
		{
			super(new BorderLayout(2, 0));
			this.min = min;
			this.max = max;
			this.value = value;
			setOpaque(false);
			field.setHorizontalAlignment(SwingConstants.CENTER);
			field.setBackground(ColorScheme.DARK_GRAY_COLOR);
			field.setForeground(Color.WHITE);
			field.setCaretColor(Color.WHITE);
			field.setBorder(new EmptyBorder(2, 2, 2, 2));
			field.setText(String.valueOf(value));
			field.setPreferredSize(new Dimension(34, 20));
			field.addActionListener(e -> commit());
			field.addFocusListener(new FocusAdapter()
			{
				@Override
				public void focusLost(FocusEvent e)
				{
					commit();
				}
			});
			add(step("−", -1), BorderLayout.WEST);
			add(field, BorderLayout.CENTER);
			add(step("+", 1), BorderLayout.EAST);
		}

		void onChange(IntConsumer onChange)
		{
			this.onChange = onChange;
		}

		/** Shows a value without telling the listener, for keeping in step with the config. */
		void setValue(int value)
		{
			this.value = value;
			if (!field.hasFocus())
			{
				field.setText(String.valueOf(value));
			}
		}

		private JComponent step(String sign, int by)
		{
			JLabel label = new JLabel(sign, SwingConstants.CENTER);
			label.setForeground(Color.WHITE);
			label.setOpaque(true);
			label.setBackground(new Color(46, 46, 46));
			label.setPreferredSize(new Dimension(20, 20));
			label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			label.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					label.setBackground(new Color(58, 58, 58));
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					label.setBackground(new Color(46, 46, 46));
				}

				@Override
				public void mouseClicked(MouseEvent e)
				{
					set(value + by);
				}
			});
			return label;
		}

		private void commit()
		{
			try
			{
				set(Integer.parseInt(field.getText().trim()));
			}
			catch (NumberFormatException e)
			{
				field.setText(String.valueOf(value));
			}
		}

		private void set(int next)
		{
			int clamped = Math.max(min, Math.min(max, next));
			field.setText(String.valueOf(clamped));
			if (clamped != value)
			{
				value = clamped;
				onChange.accept(clamped);
			}
		}
	}

	/** A colour swatch, with a checkerboard behind it to show see-through colours. Click to change. */
	static final class Swatch extends JComponent
	{
		private Color colour;

		Swatch(Color colour, Runnable onClick)
		{
			this.colour = colour;
			setPreferredSize(new Dimension(48, 20));
			setMinimumSize(getPreferredSize());
			setMaximumSize(getPreferredSize());
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					onClick.run();
				}
			});
		}

		void setColour(Color colour)
		{
			this.colour = colour;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			int w = getPreferredSize().width;
			int h = getPreferredSize().height;
			for (int x = 0; x < w; x += 5)
			{
				for (int y = 0; y < h; y += 5)
				{
					g2.setColor(((x + y) / 5) % 2 == 0 ? new Color(90, 90, 90) : new Color(60, 60, 60));
					g2.fillRect(x, y, 5, 5);
				}
			}
			g2.setColor(colour);
			g2.fillRect(0, 0, w, h);
			g2.setColor(new Color(110, 110, 110));
			g2.drawRect(0, 0, w - 1, h - 1);
			g2.dispose();
		}
	}
}
