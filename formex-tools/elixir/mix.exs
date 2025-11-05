defmodule XmlIngest.MixProject do
  use Mix.Project
  
  def project do
    [
      app: :xml_ingest,
      version: "0.1.0",
      elixir: "~> 1.15",
      start_permanent: Mix.env() == :prod,
      deps: deps()
    ]
  end
  
  def application do
    [
      extra_applications: [:logger, :crypto],
      mod: {XmlIngest.Application, []}
    ]
  end
  
  defp deps do
    [
      {:broadway, "~> 1.0"},
      {:req, "~> 0.4"},
      {:duckdbex, "~> 0.3"},
      {:saxy, "~> 1.5"},
      {:jason, "~> 1.4"},
      {:credo, "~> 1.7", only: [:dev, :test], runtime: false}
    ]
  end
end
