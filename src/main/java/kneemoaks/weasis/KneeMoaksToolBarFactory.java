package kneemoaks.weasis;

import java.util.Hashtable;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableFactory;

/** Adds the "MOAKS marker" button to the DICOM viewer's toolbar. */
@org.osgi.service.component.annotations.Component(
    service = InsertableFactory.class,
    property = {"org.weasis.dicom.viewer2d.View2dContainer=true"})
public class KneeMoaksToolBarFactory implements InsertableFactory {

  @Override
  public Type getType() {
    return Type.TOOLBAR;
  }

  @Override
  public Insertable createInstance(Hashtable<String, Object> properties) {
    return new KneeMoaksToolBar();
  }

  @Override
  public void dispose(Insertable component) {
    if (component instanceof KneeMoaksToolBar bar) {
      bar.dispose();
    }
  }

  @Override
  public boolean isComponentCreatedByThisFactory(Insertable component) {
    return component instanceof KneeMoaksToolBar;
  }
}
