import sys
sys.dont_write_bytecode = True

import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location('provision', Path(__file__).with_name('provision-immich.py'))
provision = importlib.util.module_from_spec(spec)
spec.loader.exec_module(provision)


class ProvisioningTest(unittest.TestCase):
    def test_preserves_other_settings_and_escapes_key(self):
        result = provision.build_preferences(b'<map><int name="other" value="7"/><string name="apiKey">old</string></map>',
                                             'https://example.com', 'synthetic-test-key&<>')
        root = ET.fromstring(result)
        self.assertEqual('7', root.find("int[@name='other']").get('value'))
        self.assertEqual('synthetic-test-key&<>', root.find("string[@name='apiKey']").text)
        self.assertEqual(1, len(root.findall("string[@name='apiKey']")))
        self.assertEqual('false', root.find("boolean[@name='disableSSLVerification']").get('value'))
        self.assertEqual('false', root.find("boolean[@name='debug_mode']").get('value'))

    def test_rejects_nonprivate_file(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'key'
            path.write_text('synthetic-test-key')
            path.chmod(0o644)
            with self.assertRaisesRegex(ValueError, 'must be private'):
                provision.read_key(path)

    def test_rejects_binary_without_showing_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'key'
            path.write_bytes(bytes([255, 254]) + b'secret')
            path.chmod(0o600)
            with self.assertRaisesRegex(ValueError, 'not UTF-8 plain text') as caught:
                provision.read_key(path)
            self.assertNotIn('secret', str(caught.exception))

    def test_server_rejects_credentials_and_api_suffix(self):
        for url in ['http://example.com', 'https://name:password@example.com', 'https://example.com/api']:
            with self.assertRaises(ValueError):
                provision.normalize_server(url)
        self.assertEqual('https://example.com', provision.normalize_server('https://example.com/'))

    def test_default_is_dry_run_and_does_not_touch_adb(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'key'
            path.write_text('synthetic-test-key')
            path.chmod(0o600)
            with patch('sys.argv', ['provision-immich.py', '--key-file', str(path)]):
                with patch.object(provision, 'adb') as adb:
                    with patch('builtins.print') as output:
                        self.assertEqual(0, provision.main())
                        adb.assert_not_called()
                        self.assertNotIn('synthetic-test-key', str(output.call_args_list))


if __name__ == '__main__':
    unittest.main()
