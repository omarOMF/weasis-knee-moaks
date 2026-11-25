package kneemoaks.weasis;

import java.util.Hashtable;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableFactory;

/** Registers the Knee MOAKS panel with the Weasis DICOM viewer. */
@org.osgi.service.component.annotations.Component(
    service = InsertableFactory.class,
    property = {"org.weasis.dicom.viewer2d.View2dContainer=true"})
public class KneeMoaksToolFactory implements InsertableFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(KneeMoaksToolFactory.class);

  private KneeMoaksTool tool;

  @Override
  public Type getType() {
    return Type.TOOL;
  }

  @Override
  public Insertable createInstance(Hashtable<String, Object> properties) {
    if (tool == null) {
      tool = new KneeMoaksTool(getType());
    }
    return tool;
  }

  @Override
  public void dispose(Insertable component) {
    if (tool != null) {
      tool.dispose();
      tool = null;
    }
  }

  @Override
  public boolean isComponentCreatedByThisFactory(Insertable component) {
    return component instanceof KneeMoaksTool;
  }

  @Activate
  protected void activate() {
    LOGGER.info("Knee MOAKS panel available");
  }

  @Deactivate
  protected void deactivate() {
    if (tool != null) {
      tool.dispose();
      tool = null;
    }
    LOGGER.info("Knee MOAKS panel removed");
  }
}
