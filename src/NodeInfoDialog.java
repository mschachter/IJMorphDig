import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.border.*;


public class NodeInfoDialog extends JDialog implements ActionListener
{
	protected Node node;
		
	NodeAttributePanel idPanel, diaPanel, xPanel, yPanel, zPanel, regPanel;
		
	protected Morph_Dig parent;
	
	protected boolean cancelled;
	
	protected boolean newNode;
	
	protected JButton saveButton, removeButton, cancelButton;
	
	public NodeInfoDialog(Morph_Dig p)
	{
		super(p.getWindow());
		setTitle("Node Info");
		setSize(300, 300);
		setModal(true);
		getContentPane().setBackground(Color.white);
		
		parent = p;
		cancelled = false;
		newNode = false;
		constructContent();	
	}
	
	protected void constructContent()
	{		
		Container contentPane = getContentPane();
				
		BoxLayout bl = new BoxLayout(contentPane, BoxLayout.PAGE_AXIS);
        contentPane.setLayout(bl);
        
        idPanel = new NodeAttributePanel();
        diaPanel = new NodeAttributePanel();
        xPanel = new NodeAttributePanel();
        yPanel = new NodeAttributePanel();
        zPanel = new NodeAttributePanel();
        regPanel = new NodeAttributePanel();
        
        idPanel.setName( " ID: ");
        diaPanel.setName("DIA (um): ");
        xPanel.setName(  "  X (um): ");
        yPanel.setName(  "  Y (um): ");
        zPanel.setName(  "  Z (um): ");
        regPanel.setName( "  REGION: ");
        
        idPanel.setValue("");        
        diaPanel.setValue("");
        xPanel.setValue("");
        yPanel.setValue("");
        zPanel.setValue("");
        regPanel.setValue("");
        
        idPanel.setEditable(false);
        
        contentPane.add(idPanel);
        contentPane.add(diaPanel);
        contentPane.add(xPanel);
        contentPane.add(yPanel);
        contentPane.add(zPanel);
        contentPane.add(regPanel);
        
        saveButton = new JButton();
        saveButton.setText("Save");
        saveButton.setActionCommand("SAVE");
        saveButton.addActionListener(this);

        removeButton = new JButton();
        removeButton.setText("Remove");
        removeButton.setActionCommand("REMOVE");
        removeButton.addActionListener(this);
                
        cancelButton = new JButton();
        cancelButton.setText("Cancel");
        cancelButton.setActionCommand("CANCEL");
        cancelButton.addActionListener(this);
        
        JPanel buttonPanel = new JPanel();
        Border b = BorderFactory.createEmptyBorder();
        buttonPanel.setBorder(b);
        
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
        buttonPanel.add(saveButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(cancelButton);
        
        contentPane.add(buttonPanel);
        
        pack();
	}
	
	public boolean isCancelled() { return cancelled; }
	
	public boolean isNewNode() { return newNode; }
	public void setNewNode(boolean b)
	{
		newNode = b;
		removeButton.setEnabled(!newNode);
	}
	
	public void setNode(Node n)
	{
		node = n;
		cancelled = false;
		
		setTitle("Node " + node.id);
		idPanel.setValue("" + node.id);
		diaPanel.setValue("" + node.diameter);
		xPanel.setValue("" + node.location.x);
		yPanel.setValue("" + node.location.y);
		zPanel.setValue("" + node.location.z);
		regPanel.setValue(node.region);
	}	
	
	public Node getNode() { return node; }
		
	public void actionPerformed(ActionEvent e)
	{
		if (e.getActionCommand().equals("SAVE")) {			
			Node oldNode = new Node();
			oldNode.id = node.id;
			oldNode.diameter = node.diameter;
			oldNode.location.x = node.location.x;
			oldNode.location.y = node.location.y;
			oldNode.location.z = node.location.z;
			
			node.id = Integer.parseInt(idPanel.getValue());
			node.diameter = Double.parseDouble(diaPanel.getValue());
			node.location.x = Double.parseDouble(xPanel.getValue());
			node.location.y = Double.parseDouble(yPanel.getValue());
			node.location.z = Double.parseDouble(zPanel.getValue());
			node.region = regPanel.getValue();
			
			parent.fireNodeModified(node, oldNode);
			
		} else if (e.getActionCommand().equals("REMOVE")) {			
			parent.fireNodeRemoved(node);	
		} else if (e.getActionCommand().equals("CANCEL")) {
			cancelled = true;
		}
		setVisible(false);
	}	
}

class NodeAttributePanel extends JPanel
{
	protected JLabel nameLabel;
	
	protected JTextField valueField;
	
	public NodeAttributePanel()
	{
		nameLabel = new JLabel();
		valueField = new JTextField();
		
		nameLabel.setPreferredSize(new Dimension(70, 25));
		nameLabel.setMaximumSize(new Dimension(70, 25));
		
		valueField.setPreferredSize(new Dimension(220, 25));
		valueField.setMaximumSize(new Dimension(220, 25));
		
		Border b = BorderFactory.createEmptyBorder();
		
		setBorder(b);
		setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
		add(nameLabel);
		add(valueField);
	}
	
	public void setName(String n) { nameLabel.setText(n); }
	public String getName() { return nameLabel.getText(); }
	
	public void setValue(String v) { valueField.setText(v); }
	public String getValue() { return valueField.getText(); }
	
	public void setEditable(boolean mod)
	{
		valueField.setEditable(mod);
	}
}

