package org.jboss.set;

import org.infinispan.marshall.core.ExternalPojo;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.io.Serializable;

/**
 * @author Ryan Emerson
 * @since 9.1
 */
@Entity
public class TestObject implements Serializable, ExternalPojo {

   @Id
   private int k;

   @Basic
   private int value;

   public TestObject() {
   }

   TestObject(int k) {
      this.k = k;
      this.value = k;
   }

   public int getK() {
      return k;
   }

   public void setK(int k) {
      this.k = k;
   }

   public int getValue() {
      return value;
   }

   public void setValue(int value) {
      this.value = value;
   }

   @Override
   public String toString() {
      return "TestObject{" +
            "k=" + k +
            ", value=" + value +
            '}';
   }
}