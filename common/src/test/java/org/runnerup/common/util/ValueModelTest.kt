package org.runnerup.common.util

import java.util.UUID
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class ValueModelTest {
  private lateinit var sut: ValueModel<TestObject>

  @Before
  fun setUp() {
    sut = ValueModel()
  }

  @Suppress("UNCHECKED_CAST")
  private fun mockListener(): ValueModel.ChangeListener<TestObject> =
    mock(ValueModel.ChangeListener::class.java) as ValueModel.ChangeListener<TestObject>

  @Test
  fun shouldCallListenerWithNullAsOldValueWhenCallingSet() {
    val newValue = TestObject()
    val listener = mockListener()
    sut.registerChangeListener(listener)

    sut.set(newValue)

    verify(listener).onValueChanged(sut, null, newValue)
  }

  @Test
  fun shouldCallListenerWithOldValueWhenCallingSet() {
    val oldValue = TestObject()
    val newValue = TestObject()
    val listener = mockListener()
    sut.registerChangeListener(listener)

    sut.set(oldValue)
    sut.set(newValue)

    verify(listener).onValueChanged(sut, null, oldValue)
    verify(listener).onValueChanged(sut, oldValue, newValue)
  }

  @Test
  fun shouldNotCallListenerIfValueDidNotChange() {
    val newValue = TestObject()
    val listener = mockListener()
    sut.registerChangeListener(listener)

    sut.set(newValue)
    sut.set(newValue)

    verify(listener).onValueChanged(sut, null, newValue)
    verify(listener, never()).onValueChanged(sut, newValue, newValue)
  }

  @Test
  fun shouldNotCallListenerIfValueIsNull() {
    val listener = mockListener()
    sut.registerChangeListener(listener)

    sut.set(null)

    verify(listener, never()).onValueChanged(eq(sut), any(), any())
  }

  @Test
  fun shouldNotCallListenerIfListenerIsRemoved() {
    val newValue = TestObject()
    val listener = mockListener()
    sut.registerChangeListener(listener)
    sut.unregisterChangeListener(listener)
    sut.set(newValue)

    verify(listener, never()).onValueChanged(eq(sut), any(), any())
  }

  @Test
  fun shouldReturnSetValue() {
    val newValue = TestObject()
    sut.set(newValue)

    assertThat(sut.get(), `is`(equalTo<TestObject?>(newValue)))
  }

  @Test
  fun shouldReturnNullIfNoValueSet() {
    assertThat(sut.get(), `is`(nullValue()))
  }

  @Test
  fun shouldNotCallListenersIfClearIsCalled() {
    val newValue = TestObject()
    val listener1 = mockListener()
    val listener2 = mockListener()
    val listener3 = mockListener()
    sut.registerChangeListener(listener1)
    sut.registerChangeListener(listener2)
    sut.registerChangeListener(listener3)

    sut.clearListeners()

    sut.set(newValue)

    verify(listener1, never()).onValueChanged(eq(sut), any(), any())
    verify(listener2, never()).onValueChanged(eq(sut), any(), any())
    verify(listener3, never()).onValueChanged(eq(sut), any(), any())
  }

  @Test
  fun shouldCallMultipleListeners() {
    val newValue = TestObject()
    val listener1 = mockListener()
    val listener2 = mockListener()
    val listener3 = mockListener()
    sut.registerChangeListener(listener1)
    sut.registerChangeListener(listener2)
    sut.registerChangeListener(listener3)

    sut.set(newValue)

    verify(listener1).onValueChanged(sut, null, newValue)
    verify(listener2).onValueChanged(sut, null, newValue)
    verify(listener3).onValueChanged(sut, null, newValue)
  }

  @Test(expected = IllegalArgumentException::class)
  fun shouldThrowIllegalArgumentExceptionIfListenerIsNullWhenRegister() {
    sut.registerChangeListener(null)
  }

  @Test(expected = IllegalArgumentException::class)
  fun shouldThrowIllegalArgumentExceptionIfListenerIsNullWhenUnregister() {
    sut.unregisterChangeListener(null)
  }

  @Test
  fun shouldGetValueSetInConstructor() {
    val value = TestObject()
    val valueModel = ValueModel(value)

    assertThat(valueModel.get(), `is`(equalTo<TestObject?>(value)))
  }

  private class TestObject {
    val random: UUID = UUID.randomUUID()

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is TestObject) return false
      return random == other.random
    }

    override fun hashCode(): Int = random.hashCode()

    override fun toString(): String = "TestObject{random=$random}"
  }
}
